package de.codesourcery.inversek;


public interface ISolver {
	
	public static enum Outcome 
	{
		PROCESSING,SUCCESS,FAILURE;
	}
	
	public Outcome solve(int maxIterations); 
	
	public KinematicsChain getChain();
	
	public boolean hasFinished();
}
