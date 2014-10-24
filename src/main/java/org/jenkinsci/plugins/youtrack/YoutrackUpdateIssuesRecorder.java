package org.jenkinsci.plugins.youtrack;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class YoutrackUpdateIssuesRecorder extends Recorder {
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        try {
            YoutrackIssueUpdater updater = new YoutrackIssueUpdater();
            updater.update(build, listener, build.getChangeSet());
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return true;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        private DescriptorImpl() {
            super(YoutrackUpdateIssuesRecorder.class);
        }

        @Override
        public String getDisplayName() {
            // Displayed in the publisher section
            return Messages.YoutrackUpdateIssuesRecorder_DisplayName();

        }


        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData) {
            return new YoutrackUpdateIssuesRecorder();
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }

}
