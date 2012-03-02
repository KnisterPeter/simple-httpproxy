package com.sinnerschrader.simple.httpproxy;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * @author marwol
 */
public class Main {

  public static void main(String[] args) throws Exception {
    CamelContext camelContext = new DefaultCamelContext();
    camelContext.addRoutes(new RouteBuilder() {
      @Override
      public void configure() throws Exception {
        // @formatter:off
        from("jetty:http://0.0.0.0:1234/?matchOnUriPrefix=true")
          .process(new Processor() {
            public void process(Exchange exchange) throws Exception {
              decideRouting(exchange);
            }
          })
          .choice()
            .when(header("proxy").isEqualTo("true")).to("http://localhost/?throwExceptionOnFailure=false&httpClient.cookiePolicy=ignoreCookies")
          .end();
        // @formatter:on
      }
    });
    camelContext.start();
  }

  private static void decideRouting(Exchange exchange) throws Exception {
    HttpServletRequest request = exchange.getIn().getBody(HttpServletRequest.class);
    Cookie proxyHostCookie = getProxyHost(request);
    if (proxyHostCookie == null) {
      String proxyHost = request.getParameter("proxyHost");
      if (proxyHost == null || "".equals(proxyHost)) {
        throw new ServletException("Parameter 'proxyHost' required");
      }
      HttpServletResponse response = exchange.getIn().getBody(HttpServletResponse.class);
      response.addCookie(new Cookie("proxyHost", proxyHost));
      response.sendRedirect(exchange.getIn().getHeader("CamelHttpPath", String.class));
    } else {
      exchange.getOut().setHeaders(exchange.getIn().getHeaders());
      exchange.getOut().getHeaders().put("proxy", "true");
      exchange.getOut().getHeaders().put(Exchange.HTTP_URI, proxyHostCookie.getValue());
      exchange.getOut().setBody(exchange.getIn().getBody());
    }
  }

  private static Cookie getProxyHost(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    for (Cookie cookie : cookies) {
      if ("proxyHost".equals(cookie.getName())) {
        return cookie;
      }
    }
    return null;
  }

}
