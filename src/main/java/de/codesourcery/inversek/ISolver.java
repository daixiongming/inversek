package de.codesourcery.inversek;


public interface ISolver {
	
	public static enum Outcome 
	{
		PROCESSING,SUCCESS,FAILURE;
	}
	
	public Outcome solve(); 
	
	public boolean hasFinished();
}
