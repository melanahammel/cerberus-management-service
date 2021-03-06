/*
 * Copyright (c) 2017 Nike, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.nike.cerberus.endpoints.sdb;

import com.google.common.collect.Maps;
import com.nike.backstopper.exception.ApiException;
import com.nike.cerberus.domain.SafeDepositBox;
import com.nike.cerberus.domain.SafeDepositBoxV2;
import com.nike.cerberus.error.DefaultApiError;
import com.nike.cerberus.security.CmsRequestSecurityValidator;
import com.nike.cerberus.security.VaultAuthPrincipal;
import com.nike.cerberus.service.SafeDepositBoxService;
import com.nike.riposte.server.http.RequestInfo;
import com.nike.riposte.server.http.ResponseInfo;
import com.nike.riposte.server.http.StandardEndpoint;
import com.nike.riposte.util.AsyncNettyHelper;
import com.nike.riposte.util.Matcher;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;

import javax.inject.Inject;
import javax.ws.rs.core.SecurityContext;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static io.netty.handler.codec.http.HttpHeaders.Names.LOCATION;

/**
 * Creates a new safe deposit box.  Returns the assigned unique identifier.
 */
public class CreateSafeDepositBoxV2 extends StandardEndpoint<SafeDepositBoxV2, SafeDepositBoxV2> {

    public static final String BASE_PATH = "/v2/safe-deposit-box";

    public static final String HEADER_X_REFRESH_TOKEN = "X-Refresh-Token";

    private final SafeDepositBoxService safeDepositBoxService;

    @Inject
    public CreateSafeDepositBoxV2(final SafeDepositBoxService safeDepositBoxService) {
        this.safeDepositBoxService = safeDepositBoxService;
    }

    @Override
    public CompletableFuture<ResponseInfo<SafeDepositBoxV2>> execute(final RequestInfo<SafeDepositBoxV2> request,
                                                                     final Executor longRunningTaskExecutor,
                                                                     final ChannelHandlerContext ctx) {
        return CompletableFuture.supplyAsync(
                AsyncNettyHelper.supplierWithTracingAndMdc(() -> createSafeDepositBox(request, BASE_PATH), ctx),
                longRunningTaskExecutor
        );
    }

    private ResponseInfo<SafeDepositBoxV2> createSafeDepositBox(final RequestInfo<SafeDepositBoxV2> request,
                                                              final String basePath) {
        final Optional<SecurityContext> securityContext =
                CmsRequestSecurityValidator.getSecurityContextForRequest(request);

        if (securityContext.isPresent()) {
            final VaultAuthPrincipal vaultAuthPrincipal = (VaultAuthPrincipal) securityContext.get().getUserPrincipal();
            final SafeDepositBoxV2 safeDepositBox =
                    safeDepositBoxService.createSafeDepositBoxV2(request.getContent(), vaultAuthPrincipal.getName());

            final String location = basePath + "/" + safeDepositBox.getId();

            return ResponseInfo.newBuilder(safeDepositBox)
                    .withHeaders(new DefaultHttpHeaders()
                            .set(LOCATION, location)
                            .set(HEADER_X_REFRESH_TOKEN, Boolean.TRUE.toString()))
                    .withHttpStatusCode(HttpResponseStatus.CREATED.code())
                    .build();
        }

        throw ApiException.newBuilder().withApiErrors(DefaultApiError.AUTH_BAD_CREDENTIALS).build();
    }

    @Override
    public Matcher requestMatcher() {
        return Matcher.match(BASE_PATH, HttpMethod.POST);
    }
}
