/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages.netsettings;

import io.pebbletemplates.pebble.template.PebbleTemplate;
import de.rwth.seilgraben.seilnet.main.SeilnetConfig;
import de.rwth.seilgraben.seilnet.main.SeilnetMain;
import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.config.Permission;
import de.rwth.seilgraben.seilnet.main.db.User;
import de.rwth.seilgraben.seilnet.main.web.WebPage;
import spark.Route;
import spark.Spark;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Felix Kirchmann
 */
public class NetworkSettings extends WebPage {
    private PebbleTemplate template;

    @Override
    protected void initialize()
    {
        template = getTemplate("netsettings/dashboard");

        Spark.get(Constants.PATH_PREFIX + Constants.NETSETTINGS_PATH_PREFIX  + "/", route);
        Spark.post(Constants.PATH_PREFIX + Constants.NETSETTINGS_PATH_PREFIX + "/", doForm);
    }

    private Route route = (request, response) -> {
        if (!authorizeAllPermissions(request, response)) { return ""; }
        Map<String, Object> args = new HashMap<>();
        Messages msgs = new Messages();
        User user = getUser(request);
        if(user == null || user.getRoomAssignment() == null) { return ""; }
        if(!user.getRoomAssignment().getRoom().getCurrentUser().equals(user)) {
            msgs.addWarning("strings", "notCurrentTenant");
        }

        args.put("noSelfServiceDeviceRegistration", user.hasPermission(Permission.NO_SELF_SERVICE_DEVICE_REGISTRATION));
        args.put("dynamicIP", user.isNatIPv4Dynamic());
        args.put("dynamicIPChangeTime", Constants.TIME_FORMATTER.format(Constants.DAILY_DYNAMIC_IP_CHANGE_TIME));

        args.put("adblock", user.isAdblock());
        args.put("adblockDNS", SeilnetMain.getConfig().getAdblockDnsServer() == null ? null :
                SeilnetMain.getConfig().getAdblockDnsServer().getHostAddress());

        if(!user.hasPermission(Permission.UNLIMITED_DATA_RETENTION)) {
            args.put("oldIPsRetentionDays", SeilnetMain.getConfig().getOldIpRetentionDays());
        }

        msgs.addToTemplateArgs(args);
        return runTemplate(template, args, request);
    };

    private Route doForm = (request, response) -> {
        if (!authorizeAllPermissions(request, response)) { return ""; }

        String form = request.queryParams("doForm");

        User user = getUser(request);

        switch (form)
        {
            case "toggleDynamicIPv4": {
                synchronized (getDb()) {
                    user.setNatIPv4Dynamic(!user.isNatIPv4Dynamic());
                }
                break;
            }
            case "toggleAdblock": {
                synchronized (getDb()) {
                    user.setAdblock(!user.isAdblock());
                }
                break;
            }
        }

        response.redirect("?");
        return "";
    };
}
