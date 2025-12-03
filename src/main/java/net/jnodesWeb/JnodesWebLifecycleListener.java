package net.jnodesWeb;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class JnodesWebLifecycleListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // No automatic startup here on deploy – map starts lazily on first /map.png
        System.out.println("[JnodesWebLifecycleListener] contextInitialized");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("[JnodesWebLifecycleListener] contextDestroyed – shutting down MapRuntimeManager");
        MapRuntimeManager.shutdown();
    }
}
