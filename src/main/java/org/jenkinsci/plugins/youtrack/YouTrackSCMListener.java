package org.jenkinsci.plugins.youtrack;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.listeners.SCMListener;
import hudson.scm.ChangeLogSet;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.DescribableList;

import java.util.List;

public class YouTrackSCMListener extends SCMListener {

    @Override
    public void onChangeLogParsed(AbstractBuild<?, ?> build, BuildListener listener, ChangeLogSet<?> changeLogSet) throws Exception {
        if (build.getRootBuild().equals(build)) {
            DescribableList<Publisher, Descriptor<Publisher>> publishersList = build.getProject().getPublishersList();
            boolean hasRecorder = false;
            if (publishersList != null) {
                for (Publisher publisher : publishersList) {
                    if (publisher instanceof YoutrackUpdateIssuesRecorder) {
                        hasRecorder = true;
                        break;
                    }
                }
            }
            if (!hasRecorder) {
                YoutrackIssueUpdater youtrackIssueUpdater = getYoutrackIssueUpdater();
                youtrackIssueUpdater.update(build, listener, changeLogSet);
            }
        }
    }

    YoutrackIssueUpdater getYoutrackIssueUpdater() {
        return new YoutrackIssueUpdater();
    }


    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof YouTrackSCMListener;
    }
}
