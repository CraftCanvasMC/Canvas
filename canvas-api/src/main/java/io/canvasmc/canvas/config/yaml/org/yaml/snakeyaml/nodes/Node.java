//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark;

public abstract class Node {
    private final Mark startMark;
    protected Mark endMark;
    protected boolean resolved;
    protected Boolean useClassConstructor;
    private io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Tag tag;
    private Class<? extends Object> type;
    private boolean twoStepsConstruction;
    private String anchor;

    public Node(io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Tag tag, Mark startMark, Mark endMark) {
        this.setTag(tag);
        this.startMark = startMark;
        this.endMark = endMark;
        this.type = Object.class;
        this.twoStepsConstruction = false;
        this.resolved = true;
        this.useClassConstructor = null;
    }

    public io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Tag getTag() {
        return this.tag;
    }

    public void setTag(io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Tag tag) {
        if (tag == null) {
            throw new NullPointerException("tag in a Node is required.");
        } else {
            this.tag = tag;
        }
    }

    public Mark getEndMark() {
        return this.endMark;
    }

    public abstract NodeId getNodeId();

    public Mark getStartMark() {
        return this.startMark;
    }

    public final boolean equals(Object obj) {
        return super.equals(obj);
    }

    public Class<? extends Object> getType() {
        return this.type;
    }

    public void setType(Class<? extends Object> type) {
        if (!type.isAssignableFrom(this.type)) {
            this.type = type;
        }

    }

    public boolean isTwoStepsConstruction() {
        return this.twoStepsConstruction;
    }

    public void setTwoStepsConstruction(boolean twoStepsConstruction) {
        this.twoStepsConstruction = twoStepsConstruction;
    }

    public final int hashCode() {
        return super.hashCode();
    }

    public boolean useClassConstructor() {
        if (this.useClassConstructor == null) {
            if (!this.tag.isSecondary() && this.resolved && !Object.class.equals(this.type) && !this.tag.equals(Tag.NULL)) {
                return true;
            } else {
                return this.tag.isCompatible(this.getType());
            }
        } else {
            return this.useClassConstructor;
        }
    }

    public void setUseClassConstructor(Boolean useClassConstructor) {
        this.useClassConstructor = useClassConstructor;
    }

    /**
     * @deprecated
     */
    @Deprecated
    public boolean isResolved() {
        return this.resolved;
    }

    public String getAnchor() {
        return this.anchor;
    }

    public void setAnchor(String anchor) {
        this.anchor = anchor;
    }
}
