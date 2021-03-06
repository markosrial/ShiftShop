package com.shiftshop.service.rest.controllers;

import com.shiftshop.service.model.services.CatalogService;
import com.shiftshop.service.model.services.UserService;
import com.shiftshop.service.rest.dtos.catalog.POSProductDto;
import com.shiftshop.service.rest.dtos.user.POSUserDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static com.shiftshop.service.rest.dtos.catalog.ProductConversor.toPOSProductDtos;
import static com.shiftshop.service.rest.dtos.user.UserConversor.toPOSUserDtos;

@RestController
@RequestMapping("/pos")
public class POSController {

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private UserService userService;

    @GetMapping("/lastUpdateTimestamp")
    public LocalDateTime getLastUpdateTimestamp() {

        LocalDateTime lastProductUpdate = catalogService.getLastProductUpdatedTimestamp();
        LocalDateTime lastUserUpdate = userService.getLastUserUpdatedTimestamp();

        return  lastProductUpdate.isAfter(lastUserUpdate) ? lastProductUpdate : lastUserUpdate;
    }

    @GetMapping("/syncUsers")
    public List<POSUserDto> syncUsers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime lastUpdate) {
        return toPOSUserDtos(userService.getUpdatedUsers(lastUpdate));
    }

    @GetMapping("/syncProducts")
    public List<POSProductDto> syncProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime lastUpdate) {
        return toPOSProductDtos(catalogService.getUpdatedProducts(lastUpdate));
    }

}
