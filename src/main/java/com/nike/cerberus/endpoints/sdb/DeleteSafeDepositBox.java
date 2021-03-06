/*
 * Copyright (c) 2016 Nike, Inc.
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
 */

package com.nike.cerberus.endpoints.sdb;

import com.google.common.collect.Sets;
import com.nike.backstopper.exception.ApiException;
import com.nike.cerberus.error.DefaultApiError;
import com.nike.cerberus.security.CmsRequestSecurityValidator;
import com.nike.cerberus.security.VaultAuthPrincipal;
import com.nike.cerberus.service.SafeDepositBoxService;
import com.nike.riposte.server.http.RequestInfo;
import com.nike.riposte.server.http.ResponseInfo;
import com.nike.riposte.server.http.StandardEndpoint;
import com.nike.riposte.util.AsyncNettyHelper;
import com.nike.riposte.util.Matcher;
import com.nike.riposte.util.MultiMatcher;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.core.SecurityContext;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Endpoint for deleting a safe deposit box.
 */
public class DeleteSafeDepositBox extends StandardEndpoint<Void, Void> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    public static final String HEADER_X_REFRESH_TOKEN = "X-Refresh-Token";

    private final SafeDepositBoxService safeDepositBoxService;

    @Inject
    public DeleteSafeDepositBox(final SafeDepositBoxService safeDepositBoxService) {
        this.safeDepositBoxService = safeDepositBoxService;
    }

    @Override
    public CompletableFuture<ResponseInfo<Void>> execute(RequestInfo<Void> request, Executor longRunningTaskExecutor, ChannelHandlerContext ctx) {
        return CompletableFuture.supplyAsync(
                AsyncNettyHelper.supplierWithTracingAndMdc(() -> deleteSafeDepositBox(request), ctx),
                longRunningTaskExecutor
        );
    }

    private ResponseInfo<Void> deleteSafeDepositBox(final RequestInfo<Void> request) {
        final Optional<SecurityContext> securityContext =
                CmsRequestSecurityValidator.getSecurityContextForRequest(request);

        if (securityContext.isPresent()) {
            final VaultAuthPrincipal vaultAuthPrincipal = (VaultAuthPrincipal) securityContext.get().getUserPrincipal();

            String sdbId = request.getPathParam("id");
            Optional<String> sdbNameOptional = safeDepositBoxService.getSafeDepositBoxNameById(sdbId);
            String sdbName = sdbNameOptional.isPresent() ? sdbNameOptional.get() :
                    String.format("(Failed to lookup name from id: %s)", sdbId);
            log.info("Delete SDB Event: the principal: {} is attempting to delete sdb name: '{}' and id: '{}'",
                    vaultAuthPrincipal.getName(), sdbName, sdbId);

            safeDepositBoxService.deleteSafeDepositBox(vaultAuthPrincipal.getUserGroups(), sdbId);
            return ResponseInfo.<Void>newBuilder().withHttpStatusCode(HttpResponseStatus.OK.code())
                    .withHeaders(new DefaultHttpHeaders().set(HEADER_X_REFRESH_TOKEN, Boolean.TRUE.toString()))
                    .build();
        }

        throw ApiException.newBuilder().withApiErrors(DefaultApiError.AUTH_BAD_CREDENTIALS).build();
    }

    @Override
    public Matcher requestMatcher() {

        return MultiMatcher.match(Sets.newHashSet("/v1/safe-deposit-box/{id}","/v2/safe-deposit-box/{id}"),HttpMethod.DELETE);
    }
}
