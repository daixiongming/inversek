package de.codesourcery.inversek;

@FunctionalInterface
public interface IConstraintValidator 
{
	public boolean isInvalidConfiguration(KinematicsChain chain);
}
