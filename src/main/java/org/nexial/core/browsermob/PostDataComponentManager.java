/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.browsermob;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;

import org.nexial.core.NexialConst;

public class PostDataComponentManager {
	private List<PostDataComponent> pdcList = new ArrayList<>();
	private PostDataComponent lastPDC;

	public List<PostDataComponent> getPdcList() {
		return pdcList;
	}

	public PostDataComponent addPostDataComponent(String data, String delim) {
		String[] pd = data.split(delim);
		PostDataComponent pdc;
		String postData = pd.length > 2 ? pd[2] : "";
		if (!pd[0].isEmpty()) {
			pdc = pdcExist(Integer.parseInt(pd[0]));
			if (pdc != null) {
				pdc.addPostDataName(postData);
				lastPDC = pdc;
				return pdc;
			} else {
				lastPDC = pdcAdd(Integer.parseInt(pd[0]), pd[1], postData);
			}
		} else {
			lastPDC.addPostDataName(postData);
		}
		return lastPDC;
	}

	public PostDataComponent addPostDataComponent(int postRequestSequence, String url, String postDataName) {
		PostDataComponent pdc;

		pdc = pdcExist(postRequestSequence);
		if (pdc != null) {
			pdc.addPostDataName(postDataName);
			lastPDC = pdc;
		} else {
			lastPDC = pdcAdd(postRequestSequence, url, postDataName);
		}
		return lastPDC;
	}

	public void printPDCToFile(String file) {
		File printFile = new File(file);
		boolean first = true;
		StringBuilder buf = new StringBuilder();

		try {
			buf.append(NexialConst.OPT_HAR_POST_COLUMN_NAMES).append("\n");
			for (PostDataComponent pdc : pdcList) {
				buf.append(pdc.getPostRequestSequence()).append(",").append(pdc.getUrl()).append(",");
				List<String> names = pdc.getPostDataNames();
				for (String name : names) {
					if (first) {
						buf.append(name).append("\n");
						first = false;
					} else {
						buf.append(",,").append(name).append("\n");
					}
				}
				first = true;
			}

			FileUtils.write(printFile, buf, NexialConst.DEF_CHARSET, true);

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void addDummy() {
		PostDataComponent pdc = new PostDataComponent();
		pdc.setPostRequestSequence(getNewSequenceNumber());
		pdc.setUrl("");
		pdcList.add(pdc);
	}

	public PostDataComponent get(int index) {
		return pdcList.get(index);
	}

	public int getNewSequenceNumber() {
		return pdcList == null ? 1 : pdcList.size() + 1;
	}

	public static PostDataComponentManager merge(PostDataComponentManager primary, PostDataComponentManager secondary) {
		if (primary == null) { return secondary; }
		mergePostData(primary, secondary);
		return primary;
	}

	public static PostDataComponentManager mergeIntercepted(PostDataComponentManager primary,
	                                                        PostDataComponentManager secondary) {
		if (primary == null) { return secondary; }

		for (int i = 0; i < primary.getPdcList().size(); i++) {
			if (primary.get(i).getUrl().equalsIgnoreCase(secondary.get(0).getUrl())) { return primary; }
		}

		mergePostData(primary, secondary);
		return primary;
	}

	private PostDataComponent pdcAdd(int postRequestSequence, String url, String postDataName) {
		PostDataComponent pdc = new PostDataComponent();
		pdc.setPostRequestSequence(postRequestSequence);
		pdc.setUrl(url);
		pdc.addPostDataName(postDataName);
		lastPDC = pdc;
		pdcList.add(lastPDC);
		return lastPDC;
	}

	private PostDataComponent pdcExist(int request) {
		if (pdcList != null) {
			for (PostDataComponent existpdc : pdcList) {
				if (existpdc.getPostRequestSequence() == request) {
					return existpdc;
				}
			}
		}
		return null;
	}

	private static void mergePostData(PostDataComponentManager primary, PostDataComponentManager secondary) {
		for (int i = 0; i < secondary.getPdcList().size(); i++) {
			String url = secondary.get(i).getUrl();
			int nextSequence = primary.getNewSequenceNumber();
			for (String data : secondary.get(i).getPostDataNames()) {
				primary.addPostDataComponent(nextSequence, url, data);
			}
		}
	}
}
