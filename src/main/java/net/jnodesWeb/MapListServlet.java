package net.jnodesWeb;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/list")
public class MapListServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        // ensure maps are loaded
        MapRuntimeManager.noteActivity();
        MapRuntimeManager.ensureStarted();

        Set<String> maps = MapRuntimeManager.getLoadedMapNames();

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        StringBuilder json = new StringBuilder();
        json.append("{\"maps\":[");

        boolean first = true;
        for (String name : maps) {
            if (!first) json.append(",");
            first = false;
            json.append("\"").append(name).append("\"");
        }

        json.append("]}");

        resp.getWriter().write(json.toString());
    }
}
