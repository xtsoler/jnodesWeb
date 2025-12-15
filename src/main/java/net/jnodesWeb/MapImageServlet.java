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

@WebServlet("/image")
public class MapImageServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        // mark activity and ensure map runtime is up
        MapRuntimeManager.noteActivity();
        MapRuntimeManager.ensureStarted();

        String mapName = req.getParameter("map");

        // No default listing: require map=...
        if (mapName == null || mapName.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Missing required parameter: map (example: /image?map=office)");
            return;
        }

        // Render requested map
        mapData map = MapRuntimeManager.getMapByName(mapName);
        if (map == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND,
                    "Map not found: " + mapName + " (expected " + mapName + ".json to be loaded)");
            return;
        }

        BufferedImage img = MapScreenshotUtil.renderMapToImage(map);

        resp.setContentType("image/png");
        ImageIO.write(img, "png", resp.getOutputStream());
    }
}
