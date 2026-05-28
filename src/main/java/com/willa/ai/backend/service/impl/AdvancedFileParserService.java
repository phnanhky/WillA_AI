package com.willa.ai.backend.service.impl;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import com.willa.ai.backend.util.UploadSizeValidator;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class AdvancedFileParserService {

    private final UploadSizeValidator uploadSizeValidator;

    public Map<String, Object> parseFile(MultipartFile file) throws Exception {
        uploadSizeValidator.validateByType(file.getSize(), file.getOriginalFilename());
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
        for (byte[] bytes : renderPdfToPngBytes(file)) {
            base64Images.add("data:image/png;base64," + Base64.getEncoder().encodeToString(bytes));
        }
        return base64Images;
    }

    /** Render mỗi trang PDF thành ảnh PNG (bytes) để gửi cho AI đọc. */
    public List<byte[]> renderPdfToPngBytes(MultipartFile file) throws Exception {
        List<byte[]> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); ++page) {
                BufferedImage bim = pdfRenderer.renderImageWithDPI(page, 150);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bim, "png", baos);
                pages.add(baos.toByteArray());
            }
        }
        return pages;
    }

    /** Render PSD thành ảnh PNG (bytes). */
    public byte[] renderPsdToPngBytes(MultipartFile file) throws Exception {
        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null) {
            throw new IllegalArgumentException("Could not parse PSD file.");
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
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
