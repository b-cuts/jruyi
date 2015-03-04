/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jruyi.common;

import java.io.Serializable;

/**
 * Interface to mark objects that are identifiable with an ID.
 * 
 * @param <K>
 *            type of ID
 * @since 2.0
 */
public interface IIdentifiable<K extends Serializable> {

	/**
	 * Returns the ID identifying this object.
	 * 
	 * @return the identifier
	 */
	public K id();
}