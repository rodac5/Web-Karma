/*******************************************************************************
 * Copyright 2012 University of Southern California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 	http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This code was developed by the Information Integration Group as part 
 * of the Karma project at the Information Sciences Institute of the 
 * University of Southern California.  For more information, publications, 
 * and related projects, please see: http://www.isi.edu/integration
 ******************************************************************************/

package edu.isi.karma.rep.alignment;

import java.util.Comparator;

public class LinkPriorityComparator implements Comparator<Link> {

	@Override
	public int compare(Link o1, Link o2) {
		String p1 = getPriority(o1.getPriorityType());
		String p2 = getPriority(o2.getPriorityType());
		return p1.compareTo(p2);
	}

	private String getPriority(LinkPriorityType priorityType) {
		if (priorityType == LinkPriorityType.DirectObjectProperty) return "0";
		else if (priorityType == LinkPriorityType.IndirectObjectProperty) return "1";
		else if (priorityType == LinkPriorityType.ObjectPropertyWithOnlyDomain) return "2";
		else if (priorityType == LinkPriorityType.ObjectPropertyWithOnlyRange) return "2";
		else if (priorityType == LinkPriorityType.SubClassOf) return "3";
		else if (priorityType == LinkPriorityType.ObjectPropertyWithoutDomainAndRange) return "4";
		else return "5";
	}

}
