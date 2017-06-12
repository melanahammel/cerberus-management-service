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
 */

package com.nike.cerberus.endpoints.admin;

import com.google.inject.Inject;
import com.nike.cerberus.domain.CleanUpRequest;
import com.nike.cerberus.endpoints.AdminStandardEndpoint;
import com.nike.cerberus.security.VaultAuthPrincipal;
import com.nike.cerberus.service.CleanUpService;
import com.nike.riposte.server.http.RequestInfo;
import com.nike.riposte.server.http.ResponseInfo;
import com.nike.riposte.server.http.impl.FullResponseInfo;
import com.nike.riposte.util.AsyncNettyHelper;
import com.nike.riposte.util.Matcher;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.SecurityContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Cleans up inactive or orphaned KMS keys and IAM roles.
 *
 * This is required because orphaned CMKs, KMS key DB records, and IAM role DB records are created when an SDB is deleted.
 * Thus this endpoint exists to clean up those already existing orphaned records in the DB. This endpoint will also be
 * used to clean up future orphaned records as well, to give more granular control over KMS key deletion
 */
public class CleanUpInactiveOrOrphanedRecords extends AdminStandardEndpoint<CleanUpRequest, Void> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final int DEFAULT_KMS_KEY_INACTIVE_AFTER_N_DAYS = 30;

    private final CleanUpService cleanUpService;

    @Inject
    public CleanUpInactiveOrOrphanedRecords(CleanUpService cleanUpService) {
        this.cleanUpService = cleanUpService;
    }

    @Override
    public CompletableFuture<ResponseInfo<Void>> doExecute(final RequestInfo<CleanUpRequest> request,
                                                           final Executor longRunningTaskExecutor,
                                                           final ChannelHandlerContext ctx,
                                                           final SecurityContext securityContext) {
        return CompletableFuture.supplyAsync(
                AsyncNettyHelper.supplierWithTracingAndMdc(() -> cleanUp(request, securityContext), ctx),
                longRunningTaskExecutor
        );
    }

    private FullResponseInfo<Void> cleanUp(final RequestInfo<CleanUpRequest> request,
                                           final SecurityContext securityContext) {

        final VaultAuthPrincipal vaultAuthPrincipal = (VaultAuthPrincipal) securityContext.getUserPrincipal();
        final String principal = vaultAuthPrincipal.getName();

        log.info("Clean Up Event: the principal {} is attempting to clean up kms keys", principal);

        Integer expirationPeriodInDays = request.getContent().getExpirationPeriodInDays();
        int kmsKeysInactiveAfterNDays = (expirationPeriodInDays == null) ? DEFAULT_KMS_KEY_INACTIVE_AFTER_N_DAYS : expirationPeriodInDays;

        cleanUpService.cleanUpInactiveAndOrphanedKmsKeys(kmsKeysInactiveAfterNDays);
        cleanUpService.cleanUpOrphanedIamRoles();

        return ResponseInfo.<Void>newBuilder()
                .withHttpStatusCode(HttpResponseStatus.NO_CONTENT.code())
                .build();
    }

    @Override
    public Matcher requestMatcher() {
        return Matcher.match("/v1/cleanup", HttpMethod.PUT);
    }

}
