package com.corba.bridge;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.*;
import java.nio.file.*;

/**
 * Sert les images générées par la conversion PDF→Images
 * Accessibles via /images/{filename}
 */
@WebServlet(urlPatterns = {"/images/*"})
public class ImageServlet extends HttpServlet {

    private static final String IMAGE_DIR = "/tmp/corba_pdf_output/";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        String filename = req.getPathInfo();
        if (filename == null || filename.equals("/")) {
            res.sendError(400, "Nom de fichier requis");
            return;
        }
        // Sécurité : pas de path traversal
        filename = new File(filename).getName();
        File file = new File(IMAGE_DIR + filename);

        if (!file.exists()) {
            res.sendError(404, "Image non trouvée: " + filename);
            return;
        }

        String contentType = filename.endsWith(".png") ? "image/png" : "image/jpeg";
        res.setContentType(contentType);
        res.setContentLengthLong(file.length());

        try (InputStream is = new FileInputStream(file);
             OutputStream os = res.getOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
        }
    }
}
