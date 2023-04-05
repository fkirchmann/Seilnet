/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.firewall;

/**
 *
 * @author Felix Kirchmann
 */
public interface NetworkHostListener
{
	public void hostsUpdated(DnsmasqDhcpHostMonitor source);
}
