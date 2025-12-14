package net.jnodesWeb;

import java.io.IOException;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import jnodes3clientse.MapScreenshotUtil;
import message.mapData;

@WebServlet("/map.png")
public class MapImageServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        // mark activity and ensure map runtime is up
        MapRuntimeManager.noteActivity();
        MapRuntimeManager.ensureStarted();

        // NEW: support multiple maps by name.
        // Usage:
        //   /map.png?name=office   -> serves image for office.json
        //   /map.png              -> backward-compatible: serves default map (first loaded)
        String name = req.getParameter("name");

        mapData map;
        if (name != null && !name.isEmpty()) {
            // serve requested map by base filename
            map = MapRuntimeManager.getMapByName(name);
            if (map == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND,
                        "Map not found: " + name + " (expected " + name + ".json to be loaded)");
                return;
            }
        } else {
            // backward compatible default map
            map = MapRuntimeManager.getCurrentMap();
            if (map == null) {
                resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "Map not available (not started or failed to start)");
                return;
            }
        }

        // Render on demand (keeps old behavior)
        BufferedImage img = MapScreenshotUtil.renderMapToImage(map);

        resp.setContentType("image/png");
        // Optional: avoid caching if you always want up-to-date image
        // resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");

        ImageIO.write(img, "png", resp.getOutputStream());
        //System.out.println("user.dir = " + System.getProperty("user.dir"));

    }
}
