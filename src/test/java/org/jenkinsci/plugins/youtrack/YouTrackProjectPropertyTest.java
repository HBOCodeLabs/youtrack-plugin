package org.jenkinsci.plugins.youtrack;

import com.gargoylesoftware.htmlunit.html.*;
import hudson.model.FreeStyleProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

public class YouTrackProjectPropertyTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testNotConfigured() throws IOException, SAXException {
        String projectName = "testProject1";
        FreeStyleProject project1 = j.createFreeStyleProject(projectName);
        HtmlPage configurePage = j.createWebClient().goTo("job/" + projectName + "/configure");
        HtmlForm form = configurePage.getFormByName("config");
        form.submit((HtmlButton)j.last(form.getHtmlElementsByTagName("button")));
    }

    @Test
    public void testNotEnabledButSiteConfigured() throws IOException, SAXException {
        JenkinsRule.WebClient webClient = j.createWebClient();
        HtmlPage globalConfiguration = webClient.goTo("/configure");
        HtmlForm form = globalConfiguration.getFormByName("config");

        HtmlElement youtrackSitesSection = youtrackSitesSection(globalConfiguration);
        HtmlElement addButton = j.last(youtrackSitesSection.getHtmlElementsByTagName("button"));
        addButton.click();
        HtmlElement nameInput = youtrackSitesSection.getOneHtmlElementByAttribute("input", "name", "youtrack.name");

        form.submit((HtmlButton) j.last(form.getHtmlElementsByTagName("button")));
    }

    private HtmlElement youtrackSitesSection(HtmlPage globalConfiguration) {
        HtmlElement mainPanel = globalConfiguration.getElementById("main-panel");
        List<HtmlElement> settings = mainPanel.getElementsByAttribute("td", "class", "setting-name");
        for (HtmlElement setting : settings) {
            if (setting.getTextContent().equals("YouTrack sites")) {
                return setting.getEnclosingElement("tr");
            }
        }
        return null;
    }
}