/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages.netsettings;

import com.esotericsoftware.minlog.Log;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import de.rwth.seilgraben.seilnet.main.FirewallManager;
import de.rwth.seilgraben.seilnet.main.MailSender;
import de.rwth.seilgraben.seilnet.main.SeilnetMain;
import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.config.Permission;
import de.rwth.seilgraben.seilnet.main.db.User;
import de.rwth.seilgraben.seilnet.main.web.WebPage;
import spark.Route;
import spark.Spark;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class DeviceList extends WebPage {
    private PebbleTemplate template;

    @Override
    protected void initialize()
    {
        template = getTemplate("netsettings/device_list");

        Spark.get(Constants.PATH_PREFIX + Constants.NETSETTINGS_PATH_PREFIX  + "/devices/", getRoute);
        Spark.post(Constants.PATH_PREFIX + Constants.NETSETTINGS_PATH_PREFIX  + "/devices/", postRoute);
    }

    final private Route getRoute = (request, response) -> {
        if (!authorizeAllPermissions(request, response)) { return ""; }

        Map<String, Object> args = new HashMap<>();
        Messages msgs = new Messages();
        User user = getUser(request);
        if(user == null || user.getRoomAssignment() == null) { return ""; }
        if(user.hasPermission(Permission.NO_SELF_SERVICE_DEVICE_REGISTRATION)) {
            msgs.addWarning("strings", "noSelfServiceDeviceRegistration");
        }
        if("deviceRegistered".equals(request.queryParams("okMessage"))) {
            msgs.addOk("strings", request.queryParams("okMessage"));
        }

        args.put("devicesEditable", !user.hasPermission(Permission.NO_SELF_SERVICE_DEVICE_REGISTRATION));
        args.put("devices", user.getAssignedDevices().stream()
                .sorted(Comparator.comparing(User.Device::getAssignedFrom).reversed()).collect(Collectors.toList()));
        args.put("previousDevices", user.getPreviousDevices().stream()
                .sorted(Comparator.comparing(User.Device::getAssignedFrom).reversed()).collect(Collectors.toList()));

        if (request.queryParams("showForm") != null) { args.put("showForm", request.queryParams("showForm")); }

        msgs.addToTemplateArgs(args);
        return runTemplate(template, args, request);
    };

    final private Route modifyValueRoute = (request, response) -> {
        if (!authorizeAllPermissions(request, response)) { return ""; }

        // Assume error status to return error messages in one line
        // At the end, if the request is okay, we change it back to 200
        response.status(400);

        User user = getUser(request);
        if(user.hasPermission(Permission.NO_SELF_SERVICE_DEVICE_REGISTRATION)) {
            return "Unauthorized";
        }

        String value = request.queryParams("value");

        // Empty fields not acceptable
        if((value == null || value.trim().isEmpty())) {	return i18n("strings", "fieldRequired", getLocale(request)); }

        if(request.queryParams("name").equals("deviceName")) {
            String deviceId = request.queryParams("pk");

            synchronized (getDb())
            {
                Optional<User.Device> deviceOptional = user.getAssignedDevices().stream()
                        .filter(device -> Integer.toString(device.getId()).equals(deviceId))
                        .findFirst();
                if(!deviceOptional.isPresent()) { return i18n("strings", "deviceNotFound", getLocale(request)); }
                deviceOptional.get().setName(value);
            }
        }

        response.status(200);
        return "";
    };

    final private Route processFormRoute = (request, response) -> {
        String form = request.queryParams("doForm");
        User user = getUser(request);

        if(form.equals("removeDevice")
                && !getUser(request).hasPermission(Permission.NO_SELF_SERVICE_DEVICE_REGISTRATION)) {
            int id = Integer.parseInt(request.queryParams("id"));
            user.getAssignedDevices().stream()
                    .filter(device -> device.getId() == id)
                    .findFirst()
                    .ifPresent(User.Device::unassign);
        }
        response.redirect("#");
        return "";
    };

    final private Route postRoute = (request, response) -> {
        if (!authorizeAllPermissions(request, response)) { return ""; }

        if (request.queryParams("doForm") != null) {
            return processFormRoute.handle(request, response);
        } else {
            return modifyValueRoute.handle(request, response);
        }
    };
}
