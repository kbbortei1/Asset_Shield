import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.security.MessageDigest;
import java.nio.file.Files;

// Generates the three deterministic JPEG fixtures used by the e2e security
// suite and the demo seed. Written via ImageIO so they are guaranteed to be
// decodable by the same ImageIO the dossier PDF builder uses. Re-run:
//   java e2e/fixtures/GenFixtures.java
public class GenFixtures {
    public static void main(String[] args) throws Exception {
        String dir = args.length > 0 ? args[0] : "e2e/fixtures";
        gen(dir + "/asset-1.jpg", 64, 64, new Color(40, 90, 160), "AS1");
        gen(dir + "/asset-2.jpg", 64, 64, new Color(160, 70, 40), "AS2");
        gen(dir + "/damage-1.jpg", 64, 64, new Color(60, 60, 60), "DMG");
        // Distinct pool for the k6 load test — each needs a unique sha256 so the
        // per-property/per-report duplicate-hash guards don't reject them.
        for (int i = 0; i < 12; i++) {
            gen(dir + "/load-" + i + ".jpg", 64, 64,
                new Color((i * 53) % 256, (i * 97) % 256, (i * 29) % 256), "L" + i);
        }
    }
    static void gen(String path, int w, int h, Color c, String tag) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c); g.fillRect(0, 0, w, h);
        g.setColor(Color.WHITE); g.drawString(tag, 8, 36);
        g.dispose();
        File f = new File(path);
        ImageIO.write(img, "jpg", f);
        byte[] bytes = Files.readAllBytes(f.toPath());
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        System.out.println(path + "  bytes=" + bytes.length + "  sha256=" + sb);
    }
}
