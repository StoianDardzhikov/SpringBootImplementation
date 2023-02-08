package org.example.Adapters;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.example.AnnotationApplicationContext;
import org.example.DependencyInjector.Annotations.Inject;
import org.example.DispatcherServlet;

import java.io.File;

public class TomcatAdapter {
    Tomcat tomcat;
    AnnotationApplicationContext applicationContext;

    @Inject
    public TomcatAdapter(AnnotationApplicationContext applicationContext) throws NoSuchMethodException, LifecycleException {
        this.applicationContext = applicationContext;
        this.tomcat = new Tomcat();
        initialize();
        start();
    }

    public void initialize() throws NoSuchMethodException {
        String docBase = new File(".").getAbsolutePath();
        Context context = tomcat.addContext("", docBase);
        System.out.println(applicationContext.getClasses());
        ControllersAdapter controllersAdapter = new ControllersAdapter(applicationContext.getClasses());
        DispatcherServlet dispatcherServlet = new DispatcherServlet(controllersAdapter, applicationContext);
        Tomcat.addServlet(context, "DispatcherServlet", dispatcherServlet);
        context.addServletMappingDecoded("/*", "DispatcherServlet");
    }

    public void start() throws LifecycleException {
        tomcat.start();
        tomcat.getServer().await();
    }
}
