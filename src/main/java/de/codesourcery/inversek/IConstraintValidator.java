package de.codesourcery.inversek;

@FunctionalInterface
public interface IConstraintValidator 
{
	public static IConstraintValidator NOP_VALIDATOR = chain -> true;
	
	public boolean isInvalidConfiguration(KinematicsChain chain);
}
