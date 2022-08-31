/**
* Copyright (c) otcframework.org
*
* @author  Franklin J Abel (frank.a.otc@gmail.com)
* @version 1.0
* @since   2022-08-31 
*
* This file is part of the OTC framework's JAXB ID Resolver project.
* 
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
*/
package org.otcframework.jaxb;

public class CompiledStatus {
	private boolean xmlGregorianCalendarFieldExists;
	private boolean xmlIdFieldExists;
	private boolean xmlIdRefFieldExists;
	
	public boolean isXmlGregorianCalendarFieldExists() {
		return xmlGregorianCalendarFieldExists;
	}
	public void setXmlGregorianCalendarFieldExists(
			boolean xmlGregorianCalendarFieldExists) {
		this.xmlGregorianCalendarFieldExists = xmlGregorianCalendarFieldExists;
	}
	public boolean isXmlIdFieldExists() {
		return xmlIdFieldExists;
	}
	public void setXmlIdFieldExists(boolean xmlIdFieldExists) {
		this.xmlIdFieldExists = xmlIdFieldExists;
	}
	public boolean isXmlIdRefFieldExists() {
		return xmlIdRefFieldExists;
	}
	public void setXmlIdRefFieldExists(boolean xmlIdRefFieldExists) {
		this.xmlIdRefFieldExists = xmlIdRefFieldExists;
	}
}
