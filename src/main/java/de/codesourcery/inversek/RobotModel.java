package de.codesourcery.inversek;

import java.util.ArrayList;
import java.util.List;

public class RobotModel
{
	private final List<KinematicsChain> chains=new ArrayList<>();
	
	public RobotModel() {
	}

	public RobotModel(RobotModel other) 
	{
		other.chains.forEach( chain -> chains.add(chain.createCopy() ) );
	}

	public RobotModel createCopy() {
		return new RobotModel(this);
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