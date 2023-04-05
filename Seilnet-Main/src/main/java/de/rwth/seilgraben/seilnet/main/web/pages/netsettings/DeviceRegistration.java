/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages.netsettings;

import io.pebbletemplates.pebble.template.PebbleTemplate;
import de.rwth.seilgraben.seilnet.firewall.shared.NetworkHostList;
import de.rwth.seilgraben.seilnet.main.SeilnetMain;
import de.rwth.seilgraben.seilnet.main.SimpleRateLimiter;
import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.config.Permission;
import de.rwth.seilgraben.seilnet.main.db.User;
import de.rwth.seilgraben.seilnet.main.web.WebPage;
import de.rwth.seilgraben.seilnet.main.web.WebUtil;
import de.rwth.seilgraben.seilnet.util.MacAddress;
import lombok.SneakyThrows;
import spark.Route;
import spark.Spark;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;

public class DeviceRegistration extends WebPage {
    private PebbleTemplate template;
    private static final SimpleRateLimiter<Integer> registrationRateLimiter= new SimpleRateLimiter<>(
            Constants.DEVICE_SELF_REGISTRATION_LIMIT, Constants.DEVICE_SELF_REGISTRATION_LIMIT_TIMEFRAME);

    private static final List<String> FORM_FIELDS = Collections.unmodifiableList(Arrays.asList(
            "macAddress", "macAddressCustom", "deviceName", "confirm1", "confirm2", "confirm3"));

    @Override
    protected void initialize()
    {
        template = getTemplate("netsettings/device_registration");

        Spark.get(Constants.PATH_PREFIX + Constants.NETSETTINGS_PATH_PREFIX  + "/devices/register", getRoute);
        Spark.post(Constants.PATH_PREFIX + Constants.NETSETTINGS_PATH_PREFIX  + "/devices/register", postRoute);
    }

    final private Route getRoute = (request, response) -> {
        if (!authorizeAllPermissions(request, response)) { return ""; }

        Map<String, Object> args = new HashMap<>();

        User user = getUser(request);

        if(user == null || user.getRoomAssignment() == null) { return ""; }
        if(user.hasPermission(Permission.NO_SELF_SERVICE_DEVICE_REGISTRATION)) { redirectUnauthorized(response); return ""; }

        if(user.getRoomAssignment().getRoom().getCurrentUser().equals(user)) {
            // The user is the currently active tenant of the room? Okay, show him the available devices
            args.put("unregisteredDevices", SeilnetMain.getFirewallClient().getHosts()
                    .listVlanHosts(user.getRoomAssignment().getRoom().getVlan()).stream()
                    .filter(networkHost -> user.getAssignedDevices().stream().noneMatch(device ->
                            device.getMacAddress().equals(networkHost.getMacAddress())))
                    .collect(Collectors.toList()));
        } else {
            // If this user isn't the room's active tenant (this can happen if he has sublet his room to someone else),
            // don't display any connected devices, because they probably belong to the subtenant.
            args.put("unregisteredDevices", Collections.EMPTY_LIST);
        }

        Map<String, String> formValues = new HashMap<>();
        FORM_FIELDS.stream().forEach(field -> formValues.put(field, request.queryParamOrDefault(field, "")));
        args.put("form", formValues);

        WebPage.Messages msgs = new WebPage.Messages();
        String errorMessage = request.queryParams("errorMessage");
        if(new HashSet<String>(Arrays.asList(
                "error", "invalidMacAddress", "rateLimitedDeviceRegistration", "deviceNameRequired", "macRequired"))
                .contains(errorMessage)) {
            msgs.addError("strings", errorMessage);
        }
        String warningMessage = request.queryParams("warningMessage");
        if("deviceMacAlreadyAssigned".equals(warningMessage)) {
            msgs.addWarning("strings", warningMessage);
        }
        msgs.addToTemplateArgs(args);
        return runTemplate(template, args, request);
    };

    final private Route postRoute = (request, response) -> {
        if (!authorizeAllPermissions(request, response)) { return ""; }
        User user = getUser(request);
        if(user.hasPermission(Permission.NO_SELF_SERVICE_DEVICE_REGISTRATION)) {
            redirectUnauthorized(response);
            return "";
        }
        Map<String, String> getQueryParams = FORM_FIELDS.stream().collect(
                Collectors.toMap(field -> field, field -> request.queryParamOrDefault(field, "")));

        String macAddressString = "custom".equals(request.queryParamOrDefault("macAddress", "")) ?
                                // If the user has chosen the custom mac address, use the value from the text field
                                request.queryParamOrDefault("macAddressCustom", "")
                                : request.queryParamOrDefault("macAddress", "");

        boolean deviceAssigned = false;
        String deviceName = request.queryParamOrDefault("deviceName", "");

        if(!request.queryParams().contains("registerBtn")) {
           // If the user didn't hit the register button, don't do anything
        }
        else if(macAddressString.isEmpty()) {
            getQueryParams.put("errorMessage", "macRequired");
        }
        else if(deviceName.trim().isEmpty()) {
            getQueryParams.put("errorMessage", "deviceNameRequired");
        }
        else if(request.queryParamOrDefault("confirm1", "").isEmpty()
            || request.queryParamOrDefault("confirm2", "").isEmpty()
            || request.queryParamOrDefault("confirm3", "").isEmpty()) {
            getQueryParams.put("errorMessage", "error");
        } else {
            try
            {
                MacAddress macAddress = new MacAddress(macAddressString);
                if(!registrationRateLimiter.tryAcquire(user.getId())) {
                    getQueryParams.put("errorMessage", "rateLimitedDeviceRegistration");
                } else {
                    user.assignDevice(deviceName, macAddress);
                    deviceAssigned = true;
                }
            }
            catch (User.MacAlreadyAssignedException e)
            {
                getQueryParams.put("warningMessage", "deviceMacAlreadyAssigned");
            }
            catch (IllegalArgumentException e)
            {
                getQueryParams.put("errorMessage", "invalidMacAddress");
            }
        }

        if(deviceAssigned) {
            response.redirect(Constants.PATH_PREFIX + Constants.NETSETTINGS_PATH_PREFIX
                    + "/devices/?okMessage=deviceRegistered");
        } else {
            response.redirect(Constants.PATH_PREFIX + Constants.NETSETTINGS_PATH_PREFIX
                    + "/devices/register?" + WebUtil.urlEncode(getQueryParams));
        }
        return "";
    };
}
