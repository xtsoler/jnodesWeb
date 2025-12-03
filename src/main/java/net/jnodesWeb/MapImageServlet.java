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

        // get current map snapshot
        mapData map = MapRuntimeManager.getCurrentMap();
        if (map == null) {
            resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Map not available (not started or failed to start)");
            return;
        }

        BufferedImage img = MapScreenshotUtil.renderMapToImage(map);

        resp.setContentType("image/png");
        // Optional: avoid caching if you always want up-to-date image
        // resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");

        ImageIO.write(img, "png", resp.getOutputStream());
        //System.out.println("user.dir = " + System.getProperty("user.dir"));

    }
}
