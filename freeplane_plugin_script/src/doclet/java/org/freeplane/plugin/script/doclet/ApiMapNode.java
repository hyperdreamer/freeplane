package org.freeplane.plugin.script.doclet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

final class ApiMapNode {
    private final String logicalKey;
    private final String text;
    private final String link;
    private final String contentCloneOfLogicalKey;
    private String position;
    private final boolean folded;
    private final List<ApiMapNode> children = new ArrayList<ApiMapNode>();

    ApiMapNode(String logicalKey, String text, boolean folded) {
        this(logicalKey, text, null, null, folded);
    }

    ApiMapNode(String logicalKey, String text, String link, boolean folded) {
        this(logicalKey, text, link, null, folded);
    }

    ApiMapNode(String logicalKey, String text, String link, String contentCloneOfLogicalKey, boolean folded) {
        this.logicalKey = Objects.requireNonNull(logicalKey, "logicalKey");
        this.text = contentCloneOfLogicalKey == null ? Objects.requireNonNull(text, "text") : text;
        this.link = link;
        this.contentCloneOfLogicalKey = contentCloneOfLogicalKey;
        this.folded = folded;
    }

    static ApiMapNode contentClone(String logicalKey, String contentCloneOfLogicalKey, boolean folded) {
        return new ApiMapNode(logicalKey, null, null,
            Objects.requireNonNull(contentCloneOfLogicalKey, "contentCloneOfLogicalKey"), folded);
    }

    public String getLogicalKey() {
        return logicalKey;
    }

    public String getText() {
        return text;
    }

    public String getLink() {
        return link;
    }

    public String getContentCloneOfLogicalKey() {
        return contentCloneOfLogicalKey;
    }

    public boolean isContentClone() {
        return contentCloneOfLogicalKey != null;
    }

    public String getPosition() {
        return position;
    }

    public ApiMapNode setPosition(String position) {
        this.position = position;
        return this;
    }

    public boolean isFolded() {
        return folded;
    }

    public List<ApiMapNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public void addChild(ApiMapNode child) {
        children.add(Objects.requireNonNull(child, "child"));
    }
}
