package com.corba.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.edit.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.encryption.StandardDecryptionMaterial;
import org.apache.pdfbox.exceptions.COSVisitorException;
import org.apache.pdfbox.util.PDFTextStripper;

import PDFService.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.imageio.ImageIO;

public class PDFProcessorImpl extends PDFProcessorPOA {

    private static final String OUTPUT_DIR = "/tmp/corba_pdf_output/";

    public PDFProcessorImpl() {
        new File(OUTPUT_DIR).mkdirs();
        try {
            java.security.Security.addProvider(
                new org.bouncycastle.jce.provider.BouncyCastleProvider());
            System.out.println("[IMPL] BouncyCastle OK");
        } catch (Exception e) {
            System.err.println("[IMPL] BouncyCastle: " + e.getMessage());
        }
    }

    private PDDocument loadDoc(byte[] data) throws IOException {
        return PDDocument.load(new ByteArrayInputStream(data));
    }

    private byte[] toBytes(PDDocument doc) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try { doc.save(baos); }
        catch (COSVisitorException e) { throw new IOException(e.getMessage()); }
        return baos.toByteArray();
    }

    private String cleanForCorba(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c < 128) sb.append(c);
            else {
                String norm = java.text.Normalizer.normalize(
                    String.valueOf(c), java.text.Normalizer.Form.NFD);
                for (char nc : norm.toCharArray())
                    if (nc < 128) sb.append(nc);
            }
        }
        return sb.toString();
    }

    @Override
    public byte[] mergePDF(byte[] pdf1, byte[] pdf2) throws PDFException {
        System.out.println("[CORBA] mergePDF()");
        try {
            PDDocument d1 = loadDoc(pdf1), d2 = loadDoc(pdf2), m = new PDDocument();
            List<PDPage> p1 = (List<PDPage>) d1.getDocumentCatalog().getAllPages();
            List<PDPage> p2 = (List<PDPage>) d2.getDocumentCatalog().getAllPages();
            for (PDPage p : p1) m.importPage(p);
            for (PDPage p : p2) m.importPage(p);
            byte[] r = toBytes(m); d1.close(); d2.close(); m.close(); return r;
        } catch (IOException e) { throw new PDFException("mergePDF: " + e.getMessage()); }
    }

    @Override
    public byte[] splitPDF(byte[] pdf, int s, int e) throws PDFException {
        System.out.println("[CORBA] splitPDF() " + s + "-" + e);
        try {
            PDDocument src = loadDoc(pdf), res = new PDDocument();
            List<PDPage> pages = (List<PDPage>) src.getDocumentCatalog().getAllPages();
            if (s < 1 || e > pages.size() || s > e)
                throw new PDFException("Plage invalide " + s + "-" + e);
            for (int i = s - 1; i < e; i++) res.importPage(pages.get(i));
            byte[] out = toBytes(res); src.close(); res.close(); return out;
        } catch (PDFException ex) { throw ex; }
        catch (IOException ex) { throw new PDFException("splitPDF: " + ex.getMessage()); }
    }

    @Override
    public byte[] extractPages(byte[] pdf, String[] nums) throws PDFException {
        System.out.println("[CORBA] extractPages()");
        try {
            PDDocument src = loadDoc(pdf), res = new PDDocument();
            List<PDPage> pages = (List<PDPage>) src.getDocumentCatalog().getAllPages();
            Set<Integer> req = new TreeSet<Integer>();
            for (String s : nums) req.add(Integer.parseInt(s.trim()));
            for (int n : req) {
                if (n < 1 || n > pages.size()) throw new PDFException("Page " + n + " inexistante");
                res.importPage(pages.get(n - 1));
            }
            byte[] out = toBytes(res); src.close(); res.close(); return out;
        } catch (PDFException e) { throw e; }
        catch (IOException e) { throw new PDFException("extractPages: " + e.getMessage()); }
    }

    @Override
    public byte[] deletePages(byte[] pdf, String[] nums) throws PDFException {
        System.out.println("[CORBA] deletePages()");
        try {
            PDDocument src = loadDoc(pdf), res = new PDDocument();
            List<PDPage> pages = (List<PDPage>) src.getDocumentCatalog().getAllPages();
            Set<Integer> del = new HashSet<Integer>();
            for (String s : nums) del.add(Integer.parseInt(s.trim()));
            for (int i = 0; i < pages.size(); i++)
                if (!del.contains(i + 1)) res.importPage(pages.get(i));
            byte[] out = toBytes(res); src.close(); res.close(); return out;
        } catch (IOException e) { throw new PDFException("deletePages: " + e.getMessage()); }
    }

    @Override
    public byte[] addPassword(byte[] pdf, String userPwd, String ownerPwd) throws PDFException {
        System.out.println("[CORBA] addPassword()");
        try {
            PDDocument doc = loadDoc(pdf);
            AccessPermission ap = new AccessPermission();
            ap.setCanPrint(true); ap.setCanExtractContent(false); ap.setCanModify(false);
            StandardProtectionPolicy pol = new StandardProtectionPolicy(ownerPwd, userPwd, ap);
            pol.setEncryptionKeyLength(128);
            doc.protect(pol);
            byte[] out = toBytes(doc); doc.close(); return out;
        } catch (Exception e) { throw new PDFException("addPassword: " + e.getMessage()); }
    }

    @Override
    public String[] convertToImages(byte[] pdf, String format, int dpi) throws PDFException {
        System.out.println("[CORBA] convertToImages() dpi=" + dpi);
        try {
            PDDocument doc = loadDoc(pdf);
            List<String> paths = new ArrayList<String>();
            String fmt = format.toLowerCase().contains("jpeg") ? "jpeg" : "png";
            List<PDPage> pages = (List<PDPage>) doc.getDocumentCatalog().getAllPages();
            for (int i = 0; i < pages.size(); i++) {
                BufferedImage img = pages.get(i).convertToImage(BufferedImage.TYPE_INT_RGB, dpi);
                String path = OUTPUT_DIR + "page_" + (i + 1) + "." + fmt;
                ImageIO.write(img, fmt, new File(path));
                paths.add(path);
            }
            doc.close(); return paths.toArray(new String[0]);
        } catch (IOException e) { throw new PDFException("convertToImages: " + e.getMessage()); }
    }

    @Override
    public String extractText(byte[] pdf) throws PDFException {
        System.out.println("[CORBA] extractText()");
        try {
            PDDocument doc = loadDoc(pdf);
            String text = new PDFTextStripper().getText(doc);
            doc.close();
            return cleanForCorba(text);
        } catch (IOException e) { throw new PDFException("extractText: " + e.getMessage()); }
    }

    @Override
    public byte[] createPDF(String title, String content) throws PDFException {
        System.out.println("[CORBA] createPDF()");
        try {
            PDDocument doc = new PDDocument();
            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle(title); info.setCreator("CORBA PDF Server 1.8");
            String[] rawLines = content.split("\n");
            List<String> lines = new ArrayList<String>();
            for (String line : rawLines) {
                String cl = line.replaceAll("[^\\x20-\\x7E]", "");
                while (cl.length() > 80) { lines.add(cl.substring(0, 80)); cl = cl.substring(80); }
                lines.add(cl);
            }
            int idx = 0;
            while (idx < lines.size()) {
                PDPage page = new PDPage(); doc.addPage(page);
                PDPageContentStream cs = new PDPageContentStream(doc, page);
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                cs.moveTextPositionByAmount(50, 750);
                cs.drawString(title.replaceAll("[^\\x20-\\x7E]", ""));
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.moveTextPositionByAmount(0, -30);
                int cnt = 0;
                while (idx < lines.size() && cnt < 45) {
                    cs.drawString(lines.get(idx)); cs.moveTextPositionByAmount(0, -14); idx++; cnt++;
                }
                cs.endText(); cs.close();
            }
            byte[] out = toBytes(doc); doc.close(); return out;
        } catch (IOException e) { throw new PDFException("createPDF: " + e.getMessage()); }
    }

    @Override
    public PDFMetadata getMetadata(byte[] pdf) throws PDFException {
        System.out.println("[CORBA] getMetadata()");
        try {
            PDDocument doc = loadDoc(pdf);
            PDDocumentInformation info = doc.getDocumentInformation();
            List<PDPage> pages = (List<PDPage>) doc.getDocumentCatalog().getAllPages();
            PDFMetadata m = new PDFMetadata();
            m.title = cleanForCorba(info.getTitle() != null ? info.getTitle() : "");
            m.author = cleanForCorba(info.getAuthor() != null ? info.getAuthor() : "");
            m.subject = cleanForCorba(info.getSubject() != null ? info.getSubject() : "");
            m.creator = cleanForCorba(info.getCreator() != null ? info.getCreator() : "");
            m.producer = cleanForCorba(info.getProducer() != null ? info.getProducer() : "");
            m.creationDate = info.getCreationDate() != null ?
                info.getCreationDate().getTime().toString() : "";
            m.pageCount = pages.size();
            if (!pages.isEmpty()) {
                float w = pages.get(0).getMediaBox().getWidth();
                float h = pages.get(0).getMediaBox().getHeight();
                m.pageSize = Math.round(w) + "x" + Math.round(h) + " pts";
            } else m.pageSize = "N/A";
            doc.close(); return m;
        } catch (IOException e) { throw new PDFException("getMetadata: " + e.getMessage()); }
    }

    @Override
    public byte[] setMetadata(byte[] pdf, String title, String author, String subject)
            throws PDFException {
        System.out.println("[CORBA] setMetadata()");
        try {
            PDDocument doc = loadDoc(pdf);
            PDDocumentInformation info = doc.getDocumentInformation();
            if (title != null && !title.isEmpty()) info.setTitle(title);
            if (author != null && !author.isEmpty()) info.setAuthor(author);
            if (subject != null && !subject.isEmpty()) info.setSubject(subject);
            info.setProducer("CORBA PDF Server 1.8");
            byte[] out = toBytes(doc); doc.close(); return out;
        } catch (IOException e) { throw new PDFException("setMetadata: " + e.getMessage()); }
    }

    @Override
    public TextStats extractTextWithStats(byte[] pdf) throws PDFException {
        System.out.println("[CORBA] extractTextWithStats()");
        try {
            PDDocument doc = loadDoc(pdf);
            String text = cleanForCorba(new PDFTextStripper().getText(doc));
            List<PDPage> pages = (List<PDPage>) doc.getDocumentCatalog().getAllPages();
            doc.close();
            TextStats s = new TextStats();
            s.text = text;
            s.charCount = text.length();
            s.wordCount = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
            s.lineCount = text.split("\n").length;
            s.pageCount = pages.size();
            return s;
        } catch (IOException e) { throw new PDFException("extractTextWithStats: " + e.getMessage()); }
    }

    @Override
    public byte[] rotatePages(byte[] pdf, String[] nums, int angle) throws PDFException {
        System.out.println("[CORBA] rotatePages() angle=" + angle);
        try {
            if (angle != 90 && angle != 180 && angle != 270)
                throw new PDFException("Angle invalide: " + angle);
            PDDocument doc = loadDoc(pdf);
            List<PDPage> pages = (List<PDPage>) doc.getDocumentCatalog().getAllPages();
            Set<Integer> toRot = new HashSet<Integer>();
            if (nums.length == 1 && "all".equals(nums[0])) {
                for (int i = 1; i <= pages.size(); i++) toRot.add(i);
            } else {
                for (String s : nums) toRot.add(Integer.parseInt(s.trim()));
            }
            for (int n : toRot) {
                if (n >= 1 && n <= pages.size()) {
                    PDPage p = pages.get(n - 1);
                    p.setRotation((p.findRotation() + angle) % 360);
                }
            }
            byte[] out = toBytes(doc); doc.close(); return out;
        } catch (PDFException e) { throw e; }
        catch (IOException e) { throw new PDFException("rotatePages: " + e.getMessage()); }
    }

    @Override
    public byte[] addWatermark(byte[] pdf, WatermarkOptions options) throws PDFException {
        System.out.println("[CORBA] addWatermark() text=\"" + options.text + "\"");
        try {
            PDDocument doc = loadDoc(pdf);
            List<PDPage> pages = (List<PDPage>) doc.getDocumentCatalog().getAllPages();
            String wt = options.text.replaceAll("[^\\x20-\\x7E]", "");
            float fs = options.fontSize > 0 ? options.fontSize : 48f;
            for (PDPage page : pages) {
                float w = page.getMediaBox().getWidth();
                float h = page.getMediaBox().getHeight();
                PDPageContentStream cs = new PDPageContentStream(doc, page, true, true, true);
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, fs);
                cs.setNonStrokingColor(Color.LIGHT_GRAY);
                cs.moveTextPositionByAmount(w / 4, h / 2);
                cs.drawString(wt);
                cs.endText(); cs.close();
            }
            byte[] out = toBytes(doc); doc.close(); return out;
        } catch (IOException e) { throw new PDFException("addWatermark: " + e.getMessage()); }
    }


    @Override
    public byte[] compressPDF(byte[] pdf) throws PDFException {
        System.out.println("[CORBA] compressPDF()");
        try {
            PDDocument doc = loadDoc(pdf);
            doc.getDocumentCatalog().getAllPages();
            byte[] out = toBytes(doc); doc.close();
            System.out.println("[CORBA] compress: " + pdf.length + " -> " + out.length);
            return out;
        } catch (IOException e) { throw new PDFException("compressPDF: " + e.getMessage()); }
    }

    @Override
    public byte[] removePassword(byte[] pdf, String password) throws PDFException {
        System.out.println("[CORBA] removePassword()");
        try {
            // PDFBox 1.8 : utiliser DecryptionMaterial
            PDDocument doc = loadDoc(pdf);
            if (doc.isEncrypted()) {
                StandardDecryptionMaterial dm = new StandardDecryptionMaterial(password);
                doc.openProtection(dm);
                doc.setAllSecurityToBeRemoved(true);
            }
            byte[] out = toBytes(doc); doc.close(); return out;
        } catch (Exception e) { throw new PDFException("removePassword: " + e.getMessage()); }
    }

    @Override
    public String ping() throws PDFException {
        String msg = "CORBA PDF Server 1.8 - OK - " + new java.util.Date();
        System.out.println("[CORBA] ping() -> " + msg);
        return msg;
    }
}
