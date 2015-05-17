package de.codesourcery.inversek;


public interface ISolver {
	
	public static enum Outcome 
	{
		PROCESSING,SUCCESS,FAILURE;
	}
	
	public interface ICompletionCallback 
	{
		public void complete(ISolver solver,Outcome outcome);
	}
	
	public Outcome solve(int maxIterations); 
	
	public KinematicsChain getChain();
	
	public boolean hasFinished();
	
	public ICompletionCallback getCompletionCallback();
}
