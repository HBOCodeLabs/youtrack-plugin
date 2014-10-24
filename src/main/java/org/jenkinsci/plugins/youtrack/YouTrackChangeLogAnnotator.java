package org.jenkinsci.plugins.youtrack;

import hudson.Extension;
import hudson.MarkupText;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.scm.ChangeLogAnnotator;
import hudson.scm.ChangeLogSet;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Extension
public class YouTrackChangeLogAnnotator extends ChangeLogAnnotator {
    private static final Logger LOGGER = Logger.getLogger(YouTrackChangeLogAnnotator.class.getName());

    @Override
    public void annotate(AbstractBuild<?, ?> abstractBuild, ChangeLogSet.Entry entry, MarkupText markupText) {
        AbstractProject<?, ?> project = abstractBuild.getProject();
        YouTrackSite youTrackSite = getSiteForProject(project);
        AbstractBuild<?, ?> lastSuccessfulBuild = abstractBuild.getProject().getLastSuccessfulBuild();
        if (lastSuccessfulBuild != null) {
            YouTrackSaveProjectShortNamesAction action = lastSuccessfulBuild.getAction(YouTrackSaveProjectShortNamesAction.class);
            if (action != null) {
                List<String> shortNames = action.getShortNames();

                if (youTrackSite != null && youTrackSite.isPluginEnabled() && youTrackSite.isAnnotationsEnabled()) {

                    String msg = markupText.getText();
                    int i = 0;
                    Random random = new Random();
                    for (String shortName : shortNames) {
                        Pattern projectPattern = Pattern.compile("^(" + shortName + "-" + "(\\d+)" + ")|\\W(" + shortName + "-" + "(\\d+))");
                        Matcher matcher = projectPattern.matcher(msg);
                        while (matcher.find()) {
                            if (matcher.groupCount() >= 1) {
                                String group = matcher.group(2);
                                int matchGroup = 1;
                                if(group == null) {
                                    group = matcher.group(4);
                                    matchGroup = 3;
                                }

                                String issueId = shortName + "-" + group;
                                String commitId = "_" + entry.getMsg().hashCode() + "_"  + i++ + "_" + random.nextInt();


                                String issueUrl = getRootUrl() + project.getLastSuccessfulBuild().getUrl() + "youtrack/issue?id=" + issueId;


                                String s = "<script>\n";
                                String js =  "var tooltip = new YAHOO.widget.Tooltip(\"tt1\", {\n    context: \"" +commitId+ "\"\n});\n\nfunction updateData(cfg, data) {\n    var id = data.id;\n\n    var summaryField = data.summary;\n    var descriptionField = data.description;\n    var resolvedField = data.resolved;\n\n\n    var text;\n    var desc = \"\";\n    if(descriptionField) {\n        desc = descriptionField;\n    }\n\n    if (resolvedField == null) {\n        text = \"<h2>\" + id + \": \" + summaryField + \"</h2><p>\" + desc + \"</p>\";\n    } else {\n        text = \"<h2><del>\" + id + \": \" + summaryField + \"</del></h2><p>\" + desc + \"</p>\";\n    }\n    cfg.setProperty(\"text\", text)\n}\n\ntooltip.contextTriggerEvent.subscribe(\n    \n    \n    function (type, args) {\n        var context = args[0];\n        var cfg = this.cfg;\n        cfg.setProperty(\"text\", \"Loading data...\");\n        \n        var request = Q.ajax({\n            url:  \"" + issueUrl + "\",\n            dataType: \"json\"\n        });\n        \n        request.done(\n            function(data) {\n                updateData(cfg, data);}\n        );\n        \n    }\n);\n";

                                s += js + "\n</script>";
                                markupText.addMarkup(matcher.start(matchGroup), matcher.end(matchGroup), s + "<a title=\"test\" id=\"" + commitId + "\" href=\"" + youTrackSite.getUrl() + "/issue/" + issueId + "\">", "</a>");
                            }
                        }
                    }
                }
            }
        }
    }

    String getRootUrl() {
        return Hudson.getInstance().getRootUrl();
    }

    YouTrackSite getSiteForProject(AbstractProject<?, ?> project) {
        return getYouTrackSite(project);
    }

    private YouTrackSite getYouTrackSite(AbstractProject<?, ?> project) {
        return YouTrackSite.get(project);
    }


}
