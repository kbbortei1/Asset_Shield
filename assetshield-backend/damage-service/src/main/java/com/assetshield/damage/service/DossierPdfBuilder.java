package com.assetshield.damage.service;

import com.assetshield.damage.domain.AssetSnapshot;
import com.assetshield.damage.domain.DamagePhoto;
import com.assetshield.damage.domain.DisasterType;
import com.assetshield.damage.domain.PairingMethod;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Lays out the dossier PDF with PDFBox. Images are loaded ONE at a time via
 * the supplied loader, scaled to max 1200 px / JPEG ~0.75 and the originals
 * discarded immediately — the heap is 256 MB.
 */
@Component
public class DossierPdfBuilder {

    private static final Logger log = LoggerFactory.getLogger(DossierPdfBuilder.class);

    private static final float PAGE_W = PDRectangle.A4.getWidth();   // 595
    private static final float PAGE_H = PDRectangle.A4.getHeight();  // 842
    private static final float MARGIN = 40;
    private static final int MAX_IMAGE_EDGE = 1200;
    private static final float JPEG_QUALITY = 0.75f;
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final PDFont mono = new PDType1Font(Standard14Fonts.FontName.COURIER);

    public record PairContent(PairingMethod pairingMethod, BigDecimal distanceMeters,
                              AssetSnapshot before, DamagePhoto after) {
    }

    public record AssetRow(String description, String category, BigDecimal value, Instant capturedAt) {
    }

    public record Content(UUID dossierId, String propertyName, String propertyType, String locality,
                          String ownerName, DisasterType disasterType, Instant occurredAt,
                          Instant completedAt, Instant generatedAt, String reportDescription,
                          List<PairContent> pairs, List<DamagePhoto> unpairedPhotos,
                          List<AssetRow> distinctAssets, BigDecimal totalLoss,
                          List<ManifestService.Entry> manifestEntries, String manifestHash) {
    }

    public record BuiltPdf(byte[] bytes, int pageCount) {
    }

    public BuiltPdf build(Content content, Function<String, byte[]> imageLoader) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            coverPage(doc, content);
            pairsSection(doc, content, imageLoader);
            annexSection(doc, content, imageLoader);
            assetTable(doc, content);
            manifestPages(doc, content);
            tamperEvidencePage(doc, content);
            footers(doc, content);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return new BuiltPdf(out.toByteArray(), doc.getNumberOfPages());
        }
    }

    // ── cover ────────────────────────────────────────────────────────────────

    private void coverPage(PDDocument doc, Content c) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float y = PAGE_H - 150;
            text(cs, bold, 22, MARGIN, y, "AssetShield GH");
            y -= 30;
            text(cs, bold, 17, MARGIN, y, "Damage Evidence Dossier");
            y -= 50;
            for (String[] row : new String[][]{
                    {"Property", c.propertyName() + " (" + c.propertyType() + ")"},
                    {"Locality", c.locality()},
                    {"Owner", c.ownerName()},
                    {"Disaster type", c.disasterType().name()},
                    {"Occurred at", TS.format(c.occurredAt())},
                    {"Report completed", c.completedAt() == null ? "-" : TS.format(c.completedAt())},
                    {"Dossier generated", TS.format(c.generatedAt())},
                    {"Dossier id", c.dossierId().toString()}}) {
                text(cs, bold, 11, MARGIN, y, row[0]);
                text(cs, regular, 11, MARGIN + 130, y, row[1]);
                y -= 20;
            }
            if (c.reportDescription() != null && !c.reportDescription().isBlank()) {
                y -= 10;
                text(cs, bold, 11, MARGIN, y, "Description");
                y -= 16;
                for (String line : wrap(c.reportDescription(), regular, 10, PAGE_W - 2 * MARGIN)) {
                    text(cs, regular, 10, MARGIN, y, line);
                    y -= 14;
                }
            }
            y -= 30;
            text(cs, bold, 12, MARGIN, y, "Total estimated loss: GHS " + money(c.totalLoss()));
        }
    }

    // ── before/after pairs (one pair per page half) ─────────────────────────

    private void pairsSection(PDDocument doc, Content c, Function<String, byte[]> loader)
            throws IOException {
        if (c.pairs().isEmpty()) {
            return;
        }
        float slotH = (PAGE_H - 2 * MARGIN) / 2;
        PDPage page = null;
        PDPageContentStream cs = null;
        try {
            for (int i = 0; i < c.pairs().size(); i++) {
                if (i % 2 == 0) {
                    if (cs != null) {
                        cs.close();
                    }
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    if (i == 0) {
                        text(cs, bold, 14, MARGIN, PAGE_H - MARGIN, "Before / After Evidence Pairs");
                    }
                }
                float top = PAGE_H - MARGIN - 20 - (i % 2) * slotH;
                drawPair(doc, cs, c.pairs().get(i), i + 1, top, slotH - 20, loader);
            }
        } finally {
            if (cs != null) {
                cs.close();
            }
        }
    }

    private void drawPair(PDDocument doc, PDPageContentStream cs, PairContent pair, int number,
                          float top, float height, Function<String, byte[]> loader) throws IOException {
        text(cs, bold, 10, MARGIN, top, "Pair " + number + " — " + pair.pairingMethod()
                + (pair.distanceMeters() == null ? ""
                : ", " + pair.distanceMeters().toPlainString() + " m between captures"));

        float imgTop = top - 14;
        float imgH = height - 110;
        float imgW = (PAGE_W - 2 * MARGIN - 20) / 2;
        float beforeX = MARGIN;
        float afterX = MARGIN + imgW + 20;

        embedImage(doc, cs, loader, pair.before().objectPath(), beforeX, imgTop - imgH, imgW, imgH);
        embedImage(doc, cs, loader, pair.after().getPhotoUrl(), afterX, imgTop - imgH, imgW, imgH);

        float capY = imgTop - imgH - 12;
        caption(cs, beforeX, capY, "BEFORE — " + pair.before().description(),
                gps(pair.before().gpsLat(), pair.before().gpsLng()),
                "captured " + TS.format(pair.before().capturedAt())
                        + "   value GHS " + money(pair.before().estimatedValue()),
                pair.before().sha256Hash());
        caption(cs, afterX, capY, "AFTER — " + orDash(pair.after().getDescription()),
                gps(pair.after().getGpsLat(), pair.after().getGpsLng()),
                "captured " + TS.format(pair.after().getCapturedAt()),
                pair.after().getSha256Hash());
    }

    // ── unpaired damage photos annex ─────────────────────────────────────────

    private void annexSection(PDDocument doc, Content c, Function<String, byte[]> loader)
            throws IOException {
        if (c.unpairedPhotos().isEmpty()) {
            return;
        }
        float slotH = (PAGE_H - 2 * MARGIN) / 2;
        PDPage page = null;
        PDPageContentStream cs = null;
        try {
            for (int i = 0; i < c.unpairedPhotos().size(); i++) {
                if (i % 2 == 0) {
                    if (cs != null) {
                        cs.close();
                    }
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    if (i == 0) {
                        text(cs, bold, 14, MARGIN, PAGE_H - MARGIN,
                                "Damage Photo Annex (no pre-loss match)");
                    }
                }
                DamagePhoto photo = c.unpairedPhotos().get(i);
                float top = PAGE_H - MARGIN - 20 - (i % 2) * slotH;
                float imgH = slotH - 130;
                float imgW = PAGE_W - 2 * MARGIN - 200;
                embedImage(doc, cs, loader, photo.getPhotoUrl(), MARGIN, top - 14 - imgH, imgW, imgH);
                caption(cs, MARGIN, top - 14 - imgH - 12,
                        orDash(photo.getDescription()) + " — no pre-loss match",
                        gps(photo.getGpsLat(), photo.getGpsLng()),
                        "captured " + TS.format(photo.getCapturedAt()),
                        photo.getSha256Hash());
            }
        } finally {
            if (cs != null) {
                cs.close();
            }
        }
    }

    // ── asset metadata table ─────────────────────────────────────────────────

    private void assetTable(PDDocument doc, Content c) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        PDPageContentStream cs = new PDPageContentStream(doc, page);
        try {
            float y = PAGE_H - MARGIN;
            text(cs, bold, 14, MARGIN, y, "Paired Asset Summary");
            y -= 26;
            text(cs, bold, 9, MARGIN, y, "Description");
            text(cs, bold, 9, 300, y, "Category");
            text(cs, bold, 9, 400, y, "Value (GHS)");
            text(cs, bold, 9, 480, y, "Captured");
            y -= 16;
            for (AssetRow row : c.distinctAssets()) {
                if (y < MARGIN + 40) {
                    cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = PAGE_H - MARGIN;
                }
                text(cs, regular, 9, MARGIN, y, truncate(row.description(), 52));
                text(cs, regular, 9, 300, y, row.category());
                text(cs, regular, 9, 400, y, money(row.value()));
                text(cs, regular, 9, 480, y,
                        DateTimeFormatter.ISO_LOCAL_DATE.format(row.capturedAt().atZone(ZoneOffset.UTC)));
                y -= 14;
            }
            y -= 8;
            text(cs, bold, 10, MARGIN, y, "Total estimated loss (distinct assets)");
            text(cs, bold, 10, 400, y, money(c.totalLoss()));
        } finally {
            cs.close();
        }
    }

    // ── manifest + tamper evidence ───────────────────────────────────────────

    private void manifestPages(PDDocument doc, Content c) throws IOException {
        List<ManifestService.Entry> entries = c.manifestEntries();
        int perPage = 70;
        for (int start = 0; start < Math.max(entries.size(), 1); start += perPage) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = PAGE_H - MARGIN;
                if (start == 0) {
                    text(cs, bold, 14, MARGIN, y, "SHA-256 Manifest");
                    y -= 22;
                }
                for (int i = start; i < Math.min(start + perPage, entries.size()); i++) {
                    ManifestService.Entry entry = entries.get(i);
                    text(cs, mono, 6.5f, MARGIN, y,
                            String.format("%-5s %s", entry.label(), entry.id()));
                    y -= 8;
                    text(cs, mono, 6.5f, MARGIN + 20, y, entry.sha256());
                    y -= 10;
                }
            }
        }
    }

    private void tamperEvidencePage(PDDocument doc, Content c) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float y = PAGE_H - MARGIN;
            text(cs, bold, 14, MARGIN, y, "Tamper Evidence");
            y -= 30;
            text(cs, bold, 10, MARGIN, y, "Manifest hash (SHA-256):");
            y -= 14;
            text(cs, mono, 8.5f, MARGIN, y, c.manifestHash());
            y -= 30;
            text(cs, bold, 10, MARGIN, y, "Algorithm");
            y -= 14;
            String algorithm = "Inputs, in order: (1) the SHA-256 of every distinct paired asset "
                    + "snapshot, ordered by assetId ascending (UUID string form); (2) the SHA-256 of "
                    + "every damage photo, ordered by photo id ascending (UUID string form). The "
                    + "lowercase hex strings are joined with a single newline character (\\n), the "
                    + "result is encoded as UTF-8 and hashed with SHA-256. That digest is the "
                    + "manifest hash printed above.";
            for (String line : wrap(algorithm, regular, 9.5f, PAGE_W - 2 * MARGIN)) {
                text(cs, regular, 9.5f, MARGIN, y, line);
                y -= 13;
            }
            y -= 16;
            text(cs, bold, 10, MARGIN, y, "Independent verification");
            y -= 14;
            String verification = "Any party can verify this dossier without trusting AssetShield: "
                    + "compute the SHA-256 of each original photo file listed in the manifest and "
                    + "compare it against the printed per-file hashes; then join the printed hashes "
                    + "in the documented order with newlines and SHA-256 the result. If the outcome "
                    + "equals the manifest hash above, neither the photos nor this dossier's "
                    + "evidence list has been altered since generation. Any single changed byte in "
                    + "any photo produces a different file hash and therefore a different manifest "
                    + "hash.";
            for (String line : wrap(verification, regular, 9.5f, PAGE_W - 2 * MARGIN)) {
                text(cs, regular, 9.5f, MARGIN, y, line);
                y -= 13;
            }
        }
    }

    private void footers(PDDocument doc, Content c) throws IOException {
        int total = doc.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            PDPage page = doc.getPage(i);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                text(cs, regular, 7, MARGIN, 22, "Page " + (i + 1) + " of " + total
                        + "   |   Dossier " + c.dossierId()
                        + "   |   generated " + TS.format(c.generatedAt()));
            }
        }
    }

    // ── drawing helpers ──────────────────────────────────────────────────────

    private void embedImage(PDDocument doc, PDPageContentStream cs, Function<String, byte[]> loader,
                            String objectPath, float x, float y, float w, float h) throws IOException {
        byte[] scaled = null;
        try {
            scaled = toScaledJpeg(loader.apply(objectPath));
        } catch (Exception e) {
            log.warn("Cannot load image {}: {}", objectPath, e.getMessage());
        }
        if (scaled == null) {
            cs.addRect(x, y, w, h);
            cs.stroke();
            text(cs, regular, 8, x + 10, y + h / 2, "[image unavailable]");
            return;
        }
        PDImageXObject image = JPEGFactory.createFromByteArray(doc, scaled);
        float scale = Math.min(w / image.getWidth(), h / image.getHeight());
        float drawW = image.getWidth() * scale;
        float drawH = image.getHeight() * scale;
        cs.drawImage(image, x + (w - drawW) / 2, y + (h - drawH), drawW, drawH);
    }

    /** One image at a time: decode, downscale to max 1200 px long edge, JPEG ~0.75. */
    static byte[] toScaledJpeg(byte[] original) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
        if (source == null) {
            return null;
        }
        int longEdge = Math.max(source.getWidth(), source.getHeight());
        double factor = longEdge > MAX_IMAGE_EDGE ? (double) MAX_IMAGE_EDGE / longEdge : 1.0;
        int w = Math.max(1, (int) Math.round(source.getWidth() * factor));
        int h = Math.max(1, (int) Math.round(source.getHeight() * factor));

        BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, w, h, null);
        graphics.dispose();

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (var imageOut = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(imageOut);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private void caption(PDPageContentStream cs, float x, float y, String line1, String line2,
                         String line3, String sha256) throws IOException {
        text(cs, bold, 6.5f, x, y, truncate(line1, 70));
        text(cs, regular, 6.5f, x, y - 9, line2);
        text(cs, regular, 6.5f, x, y - 18, line3);
        text(cs, regular, 6.5f, x, y - 27, "sha256 " + sha256.substring(0, 8) + "...");
        text(cs, mono, 5f, x, y - 35, sha256);
    }

    private void text(PDPageContentStream cs, PDFont font, float size, float x, float y, String value)
            throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitize(value));
        cs.endText();
    }

    private List<String> wrap(String value, PDFont font, float size, float maxWidth) throws IOException {
        List<String> lines = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : sanitize(value).split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (font.getStringWidth(candidate) / 1000 * size > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    /** Standard-14 fonts cannot encode arbitrary Unicode — clamp to printable ASCII. */
    private static String sanitize(String value) {
        if (value == null) {
            return "-";
        }
        return value.replace('—', '-').replace('–', '-')
                .replace('‘', '\'').replace('’', '\'')
                .replace('“', '"').replace('”', '"')
                .replaceAll("[^\\x20-\\x7E]", "?");
    }

    private static String gps(BigDecimal lat, BigDecimal lng) {
        return "GPS " + lat.setScale(6, java.math.RoundingMode.HALF_UP).toPlainString()
                + ", " + lng.setScale(6, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String money(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
