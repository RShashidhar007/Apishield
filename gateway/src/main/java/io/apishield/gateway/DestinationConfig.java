package io.apishield.gateway;

import io.apishield.common.net.DestinationPolicy;
import io.apishield.common.net.DestinationRejectedException;
import io.netty.resolver.AbstractAddressResolver;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;

@Configuration
public class DestinationConfig {

    @Bean DestinationPolicy destinationPolicy(GatewayProperties p) {
        var d = p.getDestination();
        return new DestinationPolicy(new HashSet<>(d.getAllowedHosts()), new HashSet<>(d.getAllowedPorts()), d.isAllowPrivateForAllowedHosts(), d.isAllowLoopback());
    }

    /** Installs the validating resolver on the Netty client used for every upstream connection. */
    @Bean HttpClientCustomizer safeResolverCustomizer(DestinationPolicy policy) {
        return httpClient -> httpClient.resolver(new SafeAddressResolverGroup(policy)).followRedirect(false);
    }

    /**
     * Connection-time SSRF / DNS-rebinding defence: the host must be allow-listed and EVERY address that DNS returns is validated
     * by the policy at the moment the connection is made. The validated address is the one that is connected to.
     * doIsResolved() is false so even literal-IP targets pass through validation.
     */
    static final class SafeAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {
        private final DestinationPolicy policy;
        SafeAddressResolverGroup(DestinationPolicy policy) { this.policy = policy; }

        @Override
        protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) {
            AddressResolver<InetSocketAddress> delegate = DefaultAddressResolverGroup.INSTANCE.getResolver(executor);
            return new AbstractAddressResolver<>(executor, InetSocketAddress.class) {
                @Override protected boolean doIsResolved(InetSocketAddress a) { return false; }

                @Override protected void doResolve(InetSocketAddress unresolved, Promise<InetSocketAddress> promise) {
                    String host = unresolved.getHostString();
                    try { policy.validateHostAllowed(host); } catch (DestinationRejectedException e) { promise.tryFailure(e); return; }
                    delegate.resolve(unresolved).addListener((Future<InetSocketAddress> f) -> {
                        if (!f.isSuccess()) { promise.tryFailure(f.cause()); return; }
                        try { policy.validateAddress(host, f.getNow().getAddress()); promise.trySuccess(f.getNow()); }
                        catch (DestinationRejectedException e) { promise.tryFailure(e); }
                    });
                }

                @Override protected void doResolveAll(InetSocketAddress unresolved, Promise<List<InetSocketAddress>> promise) {
                    String host = unresolved.getHostString();
                    try { policy.validateHostAllowed(host); } catch (DestinationRejectedException e) { promise.tryFailure(e); return; }
                    delegate.resolveAll(unresolved).addListener((Future<List<InetSocketAddress>> f) -> {
                        if (!f.isSuccess()) { promise.tryFailure(f.cause()); return; }
                        try { for (InetSocketAddress a : f.getNow()) policy.validateAddress(host, a.getAddress()); promise.trySuccess(f.getNow()); }
                        catch (DestinationRejectedException e) { promise.tryFailure(e); }
                    });
                }

                @Override public void close() { delegate.close(); }
            };
        }
    }
}
