package de.codesourcery.inversek;

import com.badlogic.gdx.physics.box2d.Body;


public abstract class Node 
{
	public static enum NodeType 
	{
		JOINT,BONE;
	}
	
	private NodeType type;
	private String id;
	private Body body;
	
	public Node(String id,NodeType type) {
		if (type == null) {
			throw new IllegalArgumentException("type must not be NULL");
		}
		if ( id == null || id.trim().length() == 0 ) {
			throw new IllegalArgumentException("ID must not be NULL/blank");
		}
		this.id = id;
		this.type = type;
	}
	
	public final boolean hasType(NodeType t) {
		return t.equals( this.type );
	}
	
	public final NodeType getType() {
		return this.type;
	}
	
	public void setBody(Body body) {
		this.body = body;
	}
	
	public Body getBody() {
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