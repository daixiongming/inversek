package de.codesourcery.inversek;

public abstract class Node<T> 
{
	public static enum NodeType 
	{
		JOINT,BONE;
	}
	
	private NodeType type;
	private String id;
	private T body;
	
	public Node(T body,String id,NodeType type) {
		if (type == null) {
			throw new IllegalArgumentException("type must not be NULL");
		}
		if ( id == null || id.trim().length() == 0 ) {
			throw new IllegalArgumentException("ID must not be NULL/blank");
		}
		this.body = body;
		this.id = id;
		this.type = type;
	}
	
	public final boolean hasType(NodeType t) {
		return t.equals( this.type );
	}
	
	public final NodeType getType() {
		return this.type;
	}
	
	public void setBody(T body) {
		this.body = body;
	}
	
	public T getBody() {
		return body;
	}
	
	@Override
	public String toString() {
		return id;
	}
	
	public final String getId() {
		return id;
	}
}