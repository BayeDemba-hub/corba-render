package com.corba.bridge;

import PDFService.*;
import org.omg.CORBA.*;
import org.omg.CosNaming.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.*;

@WebServlet(urlPatterns = {"/api/*"})
@MultipartConfig(maxFileSize = 50 * 1024 * 1024)
public class PDFBridgeServlet extends HttpServlet {

    private PDFProcessor pdfService;

    @Override
    public void init() throws ServletException {
        System.out.println("[BRIDGE] Connexion CORBA...");
        try {
            String[] orbArgs = {
                "-ORBInitialHost", System.getenv().getOrDefault("CORBA_HOST","server"),
                "-ORBInitialPort", System.getenv().getOrDefault("CORBA_PORT","900")
            };
            ORB orb = ORB.init(orbArgs, null);
            NamingContextExt nc = NamingContextExtHelper.narrow(
                orb.resolve_initial_references("NameService"));
            pdfService = PDFProcessorHelper.narrow(nc.resolve_str("PDFProcessor"));
            System.out.println("[BRIDGE] Connecte au serveur CORBA OK");
        } catch (Exception e) {
            System.err.println("[BRIDGE] ERREUR: " + e.getMessage());
        }
    }

    @Override protected void doOptions(HttpServletRequest q, HttpServletResponse r) throws IOException { cors(r); r.setStatus(200); }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        cors(res);
        String path = req.getPathInfo();
        System.out.println("[BRIDGE] POST " + path);
        try {
            switch (path) {
                case "/merge":          handleMerge(req,res); break;
                case "/split":          handleSplit(req,res); break;
                case "/extract-pages":  handleExtractPages(req,res); break;
                case "/delete-pages":   handleDeletePages(req,res); break;
                case "/add-password":   handleAddPassword(req,res); break;
                case "/convert-images": handleConvertImages(req,res); break;
                case "/extract-text":   handleExtractText(req,res); break;
                case "/create":         handleCreate(req,res); break;
                case "/metadata":       handleMetadata(req,res); break;
                case "/set-metadata":   handleSetMetadata(req,res); break;
                case "/text-stats":     handleTextStats(req,res); break;
                case "/rotate":         handleRotate(req,res); break;
                case "/watermark":      handleWatermark(req,res); break;
                case "/compress":       handleCompress(req,res); break;
                case "/remove-password":handleRemovePassword(req,res); break;
                default: res.sendError(404,"Endpoint inconnu: "+path);
            }
        } catch (Exception e) {
            res.setStatus(500); res.setContentType("text/plain;charset=UTF-8");
            res.getWriter().write("Erreur serveur: "+e.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        cors(res);
        if ("/health".equals(req.getPathInfo())) {
            res.setContentType("application/json");
            if (pdfService != null) {
                try {
                    String pong = pdfService.ping();
                    res.setStatus(200);
                    res.getWriter().write("{\"status\":\"ok\",\"server\":\""+pong+"\"}");
                } catch(Exception e) {
                    res.setStatus(503);
                    res.getWriter().write("{\"status\":\"error\",\"msg\":\""+e.getMessage()+"\"}");
                }
            } else {
                res.setStatus(503);
                res.getWriter().write("{\"status\":\"disconnected\"}");
            }
        }
    }

    private void handleMerge(HttpServletRequest q, HttpServletResponse r) throws Exception {
        byte[] r1 = pdfService.mergePDF(partBytes(q,"pdf1"), partBytes(q,"pdf2"));
        sendPdf(r, r1, "fusionne.pdf");
    }
    private void handleSplit(HttpServletRequest q, HttpServletResponse r) throws Exception {
        byte[] res = pdfService.splitPDF(partBytes(q,"pdf"),
            Integer.parseInt(q.getParameter("start")), Integer.parseInt(q.getParameter("end")));
        sendPdf(r, res, "decoupe.pdf");
    }
    private void handleExtractPages(HttpServletRequest q, HttpServletResponse r) throws Exception {
        String[] pages = q.getParameter("pages").split(",");
        for(int i=0;i<pages.length;i++) pages[i]=pages[i].trim();
        sendPdf(r, pdfService.extractPages(partBytes(q,"pdf"), pages), "extraites.pdf");
    }
    private void handleDeletePages(HttpServletRequest q, HttpServletResponse r) throws Exception {
        String[] pages = q.getParameter("pages").split(",");
        for(int i=0;i<pages.length;i++) pages[i]=pages[i].trim();
        sendPdf(r, pdfService.deletePages(partBytes(q,"pdf"), pages), "supprimees.pdf");
    }
    private void handleAddPassword(HttpServletRequest q, HttpServletResponse r) throws Exception {
        sendPdf(r, pdfService.addPassword(partBytes(q,"pdf"),
            q.getParameter("userPassword"), q.getParameter("ownerPassword")), "protege.pdf");
    }
    private void handleConvertImages(HttpServletRequest q, HttpServletResponse r) throws Exception {
        String[] paths = pdfService.convertToImages(partBytes(q,"pdf"),
            q.getParameter("format"), Integer.parseInt(q.getParameter("dpi")));
        StringBuilder json = new StringBuilder("{\"images\":[");
        for(int i=0;i<paths.length;i++){
            json.append("\"").append("/images/").append(new File(paths[i]).getName()).append("\"");
            if(i<paths.length-1) json.append(",");
        }
        json.append("]}");
        r.setContentType("application/json;charset=UTF-8");
        r.getWriter().write(json.toString());
    }
    private void handleExtractText(HttpServletRequest q, HttpServletResponse r) throws Exception {
        String text = pdfService.extractText(partBytes(q,"pdf"));
        String esc = text.replace("\\","\\\\").replace("\"","\\\"")
            .replace("\r\n","\\n").replace("\n","\\n").replace("\r","\\n");
        r.setContentType("application/json;charset=UTF-8");
        r.getWriter().write("{\"text\":\""+esc+"\"}");
    }
    private void handleCreate(HttpServletRequest q, HttpServletResponse r) throws Exception {
        String title = q.getParameter("title");
        sendPdf(r, pdfService.createPDF(title, q.getParameter("content")),
            title.replaceAll("[^a-zA-Z0-9]","_")+".pdf");
    }
    private void handleMetadata(HttpServletRequest q, HttpServletResponse r) throws Exception {
        PDFMetadata m = pdfService.getMetadata(partBytes(q,"pdf"));
        r.setContentType("application/json;charset=UTF-8");
        r.getWriter().write("{\"title\":\""+esc(m.title)+"\",\"author\":\""+esc(m.author)+
            "\",\"subject\":\""+esc(m.subject)+"\",\"creator\":\""+esc(m.creator)+
            "\",\"producer\":\""+esc(m.producer)+"\",\"creationDate\":\""+esc(m.creationDate)+
            "\",\"pageCount\":"+m.pageCount+",\"pageSize\":\""+esc(m.pageSize)+"\"}");
    }
    private void handleSetMetadata(HttpServletRequest q, HttpServletResponse r) throws Exception {
        sendPdf(r, pdfService.setMetadata(partBytes(q,"pdf"),
            q.getParameter("title"), q.getParameter("author"), q.getParameter("subject")),
            "metadata_modifie.pdf");
    }
    private void handleTextStats(HttpServletRequest q, HttpServletResponse r) throws Exception {
        TextStats s = pdfService.extractTextWithStats(partBytes(q,"pdf"));
        String esc = s.text.replace("\\","\\\\").replace("\"","\\\"")
            .replace("\r\n","\\n").replace("\n","\\n");
        r.setContentType("application/json;charset=UTF-8");
        r.getWriter().write("{\"text\":\""+esc+"\",\"charCount\":"+s.charCount+
            ",\"wordCount\":"+s.wordCount+",\"lineCount\":"+s.lineCount+
            ",\"pageCount\":"+s.pageCount+"}");
    }
    private void handleRotate(HttpServletRequest q, HttpServletResponse r) throws Exception {
        String[] pages = q.getParameter("pages").split(",");
        for(int i=0;i<pages.length;i++) pages[i]=pages[i].trim();
        sendPdf(r, pdfService.rotatePages(partBytes(q,"pdf"), pages,
            Integer.parseInt(q.getParameter("angle"))), "tourne.pdf");
    }
    private void handleWatermark(HttpServletRequest q, HttpServletResponse r) throws Exception {
        WatermarkOptions opts = new WatermarkOptions(
            q.getParameter("text"),
            Float.parseFloat(q.getParameter("fontSize")),
            0.5f, 45f);
        sendPdf(r, pdfService.addWatermark(partBytes(q,"pdf"), opts), "watermark.pdf");
    }
    private void handleCompress(HttpServletRequest q, HttpServletResponse r) throws Exception {
        sendPdf(r, pdfService.compressPDF(partBytes(q,"pdf")), "compresse.pdf");
    }
    private void handleRemovePassword(HttpServletRequest q, HttpServletResponse r) throws Exception {
        sendPdf(r, pdfService.removePassword(partBytes(q,"pdf"),
            q.getParameter("password")), "deverrouille.pdf");
    }

    private byte[] partBytes(HttpServletRequest req, String name) throws Exception {
        Part part = req.getPart(name);
        if(part==null) throw new Exception("Fichier manquant: "+name);
        ByteArrayOutputStream baos=new ByteArrayOutputStream();
        InputStream is=part.getInputStream();
        byte[] buf=new byte[8192]; int n;
        while((n=is.read(buf))!=-1) baos.write(buf,0,n);
        return baos.toByteArray();
    }
    private void sendPdf(HttpServletResponse r, byte[] data, String name) throws IOException {
        r.setContentType("application/pdf");
        r.setHeader("Content-Disposition","attachment; filename=\""+name+"\"");
        r.setContentLength(data.length);
        r.getOutputStream().write(data);
    }
    private String esc(String s){ return s==null?"":s.replace("\\","\\\\").replace("\"","\\\""); }
    private void cors(HttpServletResponse r){
        r.setHeader("Access-Control-Allow-Origin","*");
        r.setHeader("Access-Control-Allow-Methods","GET,POST,OPTIONS");
        r.setHeader("Access-Control-Allow-Headers","Content-Type");
    }
}
