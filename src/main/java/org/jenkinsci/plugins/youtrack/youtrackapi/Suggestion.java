package org.jenkinsci.plugins.youtrack.youtrackapi;

import lombok.Getter;
import lombok.Setter;

/**
 *
 */
public class Suggestion {
    @Getter @Setter private String option;
    @Getter @Setter private String description;
    @Getter @Setter private String suffix;
    @Getter @Setter private String prefix;
    @Getter @Setter private int completionStart;
    @Getter @Setter private int completionEnd;
    @Getter @Setter private int matchStart;
    @Getter @Setter private int matchEnd;
}
