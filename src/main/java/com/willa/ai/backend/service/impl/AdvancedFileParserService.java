package com.willa.ai.backend.service.impl;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.Base64;

@Service
public class AdvancedFileParserService {

    public Map<String, Object> parseFile(MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        Map<String, Object> result = new HashMap<>();

        if (fileName.endsWith(".pdf")) {
            result.put("type", "pdf");
            result.put("pages", parsePdf(file));
        } else if (fileName.endsWith(".csv")) {
            result.put("type", "csv");
            result.put("data", parseCsv(file));
        } else if (fileName.endsWith(".psd")) {
            result.put("type", "psd");
            result.put("image", parsePsd(file));
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + fileName);
        }

        return result;
    }

    private List<String> parsePdf(MultipartFile file) throws Exception {
        List<String> base64Images = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); ++page) {
                BufferedImage bim = pdfRenderer.renderImageWithDPI(page, 150);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bim, "png", baos);
                byte[] bytes = baos.toByteArray();
                String base64Image = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
                base64Images.add(base64Image);
            }
        }
        return base64Images;
    }

    private List<List<String>> parseCsv(MultipartFile file) throws Exception {
        List<List<String>> records = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                records.add(Arrays.asList(values));
            }
        }
        return records;
    }

    private String parsePsd(MultipartFile file) throws Exception {
        // Requires TwelveMonkeys ImageIO PSD plugin.
        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null) {
            throw new Exception("Could not parse PSD file.");
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] bytes = baos.toByteArray();
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
    }
}
