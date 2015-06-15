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

package org.jruyi.io.channel;

import java.util.Map;

import org.jruyi.timeoutadmin.ITimeoutAdmin;
import org.jruyi.timeoutadmin.ITimeoutNotifier;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lmax.disruptor.util.Util;

@Component(name = "jruyi.io.channeladmin", xmlns = "http://www.osgi.org/xmlns/scr/v1.2.0")
public final class ChannelAdmin implements IChannelAdmin {

	private static final Logger c_logger = LoggerFactory.getLogger(ChannelAdmin.class);

	private static int s_msgId = -1;

	private IoThread[] m_iots;
	private int m_iotMask;

	private SelectorThread[] m_sts;
	private ITimeoutAdmin m_tm;

	@Override
	public void onRegisterRequired(ISelectableChannel channel) {
		getSelectorThread(channel.id().intValue()).onRegisterRequired(channel);
	}

	@Override
	public void onConnectRequired(ISelectableChannel channel) {
		getSelectorThread(channel.id().intValue()).onConnectRequired(channel);
	}

	@Override
	public IIoWorker designateIoWorker(ISelectableChannel channel) {
		return getIoThread(channel.id().intValue());
	}

	@Override
	public ITimeoutNotifier createTimeoutNotifier(ISelectableChannel channel) {
		return m_tm.createNotifier(channel);
	}

	@Override
	public void performIoTask(IIoTask task, Object msg) {
		getIoThread(++s_msgId).perform(task, msg, null, 0);
	}

	@Reference(name = "timeoutAdmin", policy = ReferencePolicy.DYNAMIC)
	synchronized void setTimeoutAdmin(ITimeoutAdmin tm) {
		m_tm = tm;
	}

	synchronized void unsetTimeoutAdmin(ITimeoutAdmin tm) {
		if (m_tm == tm)
			m_tm = null;
	}

	void activate(Map<String, ?> properties) throws Throwable {
		c_logger.info("Activating ChannelAdmin...");

		final int capacityOfIoRingBuffer = capacityOfIoRingBuffer(properties);

		int count = numberOfIoThreads(properties);
		final IoThread[] iots = new IoThread[count];
		for (int i = 0; i < count; ++i) {
			@SuppressWarnings("resource")
			final IoThread iot = new IoThread();
			iot.open(i, capacityOfIoRingBuffer);
			iots[i] = iot;
		}

		m_iotMask = count - 1;
		m_iots = iots;

		try {
			count = numberOfSelectors(properties);
			int capacityOfSelectorRingBuffer = capacityOfIoRingBuffer * count
					/ Runtime.getRuntime().availableProcessors();
			capacityOfSelectorRingBuffer = Util.ceilingNextPowerOfTwo(capacityOfSelectorRingBuffer);
			final SelectorThread[] sts = new SelectorThread[count];
			for (int i = 0; i < count; ++i) {
				@SuppressWarnings("resource")
				final SelectorThread st = new SelectorThread();
				try {
					st.open(i, capacityOfSelectorRingBuffer);
				} catch (Exception e) {
					st.close();
					while (i > 0)
						sts[--i].close();
					throw e;
				}
				sts[i] = st;
			}
			m_sts = sts;
		} catch (Throwable t) {
			stopIoThreads();
			throw t;
		}

		c_logger.info("ChannelAdmin activated");
	}

	void deactivate() {
		c_logger.info("Deactivating ChannelAdmin...");

		final SelectorThread[] sts = m_sts;
		m_sts = null;
		for (SelectorThread st : sts)
			st.close();

		stopIoThreads();

		c_logger.info("ChannelAdmin deactivated");
	}

	private void stopIoThreads() {
		final IoThread[] iots = m_iots;
		m_iots = null;
		for (IoThread iot : iots)
			iot.close();
	}

	private SelectorThread getSelectorThread(int id) {
		final SelectorThread[] sts = m_sts;
		final int i = (id & ~(1 << 31)) % sts.length;
		return sts[i];
	}

	private IoThread getIoThread(int id) {
		return m_iots[id & m_iotMask];
	}

	private static int capacityOfIoRingBuffer(Map<String, ?> properties) {
		final Object value = properties.get("capacityOfIoRingBuffer");
		int capacity;
		if (value == null || (capacity = (Integer) value) < 1)
			capacity = 1024 * 8;
		else
			capacity = Util.ceilingNextPowerOfTwo(capacity);

		return capacity;
	}

	private static int numberOfSelectors(Map<String, ?> properties) {
		final Object value = properties.get("numberOfSelectorThreads");
		int n;
		int i = Runtime.getRuntime().availableProcessors();
		if (value == null || (n = (Integer) value) < 1 || n >= i) {
			n = 0;
			while ((i >>>= 1) > 0)
				++n;
			if (n < 1)
				n = 1;
			int count = Util.ceilingNextPowerOfTwo(n);
			if (count > n)
				count >>>= 1;
			return count;
		}
		return n;
	}

	private static int numberOfIoThreads(Map<String, ?> properties) {
		final Object value = properties.get("numberOfIoThreads");
		int n;
		if (value == null || (n = (Integer) value) < 1)
			n = Runtime.getRuntime().availableProcessors();
		n = Util.ceilingNextPowerOfTwo(n);
		return n;
	}
}
