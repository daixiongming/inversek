package de.codesourcery.inversek;

import java.util.ArrayList;
import java.util.List;

public class Model
{
	private final List<KinematicsChain> chains=new ArrayList<>();
	
	public Model() {
	}

	public Model(Model other) 
	{
		other.chains.forEach( chain -> chains.add(chain.createCopy() ) );
	}

	public Model createCopy() {
		return new Model(this);
	}

	public void addKinematicsChain(KinematicsChain chain) {
		if ( chain == null ) {
			throw new IllegalArgumentException("chain must not be NULL");
		}
		this.chains.add(chain);
	}
	
	public List<KinematicsChain> getChains() {
		return chains;
	}
}