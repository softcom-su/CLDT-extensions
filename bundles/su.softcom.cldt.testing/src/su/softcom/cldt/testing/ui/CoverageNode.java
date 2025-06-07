package su.softcom.cldt.testing.ui;

import java.util.ArrayList;
import java.util.List;

public class CoverageNode {
	public enum NodeType {
		FOLDER, FILE, FUNCTION
	}

	private final String name;
	private final NodeType type;
	private final List<CoverageNode> children;
	private Object[] coverageData;

	public CoverageNode(String name, NodeType type) {
		this.name = name;
		this.type = type;
		this.children = new ArrayList<>();
		this.coverageData = null;
	}

	public String getName() {
		return name;
	}

	public NodeType getType() {
		return type;
	}

	public List<CoverageNode> getChildren() {
		return children;
	}

	public void addChild(CoverageNode child) {
		children.add(child);
	}

	public Object[] getCoverageData() {
		return coverageData;
	}

	public void setCoverageData(Object[] coverageData) {
		this.coverageData = coverageData;
	}
}