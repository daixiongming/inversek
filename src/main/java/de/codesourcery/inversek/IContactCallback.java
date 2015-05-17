package de.codesourcery.inversek;

import com.badlogic.gdx.physics.box2d.Contact;

public interface IContactCallback 
{
	public boolean endContact(Contact contact);
	
	public boolean beginContact(Contact contact);
}
