package de.codesourcery.inversek;


public abstract class Node 
{
	public static enum NodeType 
	{
		JOINT,BONE;
	}
	
	private NodeType type;
	private String name;
	
	public Node(String name,NodeType type) {
		if (type == null) {
			throw new IllegalArgumentException("type must not be NULL");
		}
		this.name = name;
		this.type = type;
	}
	
	public final boolean hasType(NodeType t) {
		return t.equals( this.type );
	}
	
	public final NodeType getType() {
		return this.type;
	}
	
	@Override
	public String toString() {
		return name;
	}
	
	public final String getName() {
		return name;
	}
}