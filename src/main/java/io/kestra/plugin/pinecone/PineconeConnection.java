package io.kestra.plugin.pinecone;

import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class PineconeConnection extends Task {

    @Schema(title = "Pinecone API key")
    @NotNull
    @PluginProperty(group = "connection")
    protected Property<String> apiKey;

    @Schema(
        title = "Index name",
        description = "Name of the Pinecone index to operate on."
    )
    @PluginProperty(group = "connection")
    protected Property<String> indexName;

    @Schema(
        title = "Control-plane host override",
        description = """
            Optional URL override for the Pinecone control-plane API.
            Defaults to https://api.pinecone.io when unset.
            Set this to point to a local emulator (e.g. http://localhost:5080).
            """
    )
    @PluginProperty(group = "connection")
    protected Property<String> host;

    @Schema(
        title = "Enable TLS for gRPC data-plane connections",
        description = """
            Set to false when connecting to a local emulator that does not support TLS.
            Defaults to true (production Pinecone always requires TLS).
            """
    )
    @Builder.Default
    @PluginProperty(group = "advanced")
    protected Property<Boolean> tlsEnabled = Property.ofValue(true);

    protected Pinecone buildClient(RunContext runContext) throws IllegalVariableEvaluationException {
        var rApiKey = runContext.render(apiKey).as(String.class).orElseThrow();
        var builder = new Pinecone.Builder(rApiKey);

        var rHost = runContext.render(host).as(String.class).orElse(null);
        if (rHost != null) {
            builder.withHost(rHost);
        }

        var rTlsEnabled = runContext.render(tlsEnabled).as(Boolean.class).orElse(true);
        builder.withTlsEnabled(rTlsEnabled);

        return builder.build();
    }

    protected Index getIndexConnection(RunContext runContext) throws IllegalVariableEvaluationException {
        ensurePickFirstRegistered();
        var rIndexName = runContext.render(indexName).as(String.class).orElseThrow();
        var client = buildClient(runContext);
        var indexHost = client.describeIndex(rIndexName).getHost();

        var rTlsEnabled = runContext.render(tlsEnabled).as(Boolean.class).orElse(true);
        var config = new io.pinecone.configs.PineconeConfig(
            runContext.render(apiKey).as(String.class).orElseThrow()
        );
        config.setTLSEnabled(rTlsEnabled);
        config.setHost(indexHost);

        // On Linux, gRPC's default NameResolver resolves "localhost" as a Unix socket address
        // rather than a TCP loopback address. Using forAddress(InetAddress, port) bypasses the
        // NameResolver entirely and forces a TCP connection.
        if (indexHost != null && indexHost.contains("localhost")) {
            config.setCustomManagedChannel(buildTcpChannel(indexHost, rTlsEnabled));
        }

        var sdkConnection = new io.pinecone.configs.PineconeConnection(config);
        return new Index(sdkConnection, rIndexName);
    }

    // ServiceLoader may fail to discover PickFirstLoadBalancerProvider inside Kestra's plugin
    // classloader; registering it explicitly guarantees gRPC channel creation never panics with
    // "Could not find policy 'pick_first'". The class is in io.grpc.internal and not exposed via
    // the public API, so reflection is used to stay compile-time clean.
    private static void ensurePickFirstRegistered() {
        var registry = io.grpc.LoadBalancerRegistry.getDefaultRegistry();
        if (registry.getProvider("pick_first") != null) {
            return;
        }
        try {
            var providerClass = Class.forName(
                "io.grpc.internal.PickFirstLoadBalancerProvider",
                true,
                PineconeConnection.class.getClassLoader()
            );
            registry.register((io.grpc.LoadBalancerProvider) providerClass.getDeclaredConstructor().newInstance());
        } catch (Exception ignored) {
            // best-effort; the service file in META-INF/services should cover this path
        }
    }

    private static io.grpc.ManagedChannel buildTcpChannel(String host, boolean tlsEnabled) {
        // Strip any scheme prefix the emulator may include in the host string
        var stripped = host.replaceFirst("https?://", "");
        var colon = stripped.lastIndexOf(':');
        var port = colon >= 0 ? Integer.parseInt(stripped.substring(colon + 1)) : (tlsEnabled ? 443 : 80);

        // InetSocketAddress with a numeric IP bypasses gRPC's NameResolver entirely,
        // ensuring a TCP connection instead of a Unix socket.
        var address = new InetSocketAddress("127.0.0.1", port);
        var builder = NettyChannelBuilder.forAddress(address);
        if (tlsEnabled) {
            builder.negotiationType(NegotiationType.TLS);
        } else {
            builder.negotiationType(NegotiationType.PLAINTEXT);
        }
        return builder.build();
    }

    /** Converts a {@code Map<String, Object>} to a protobuf {@link Struct}, used for metadata and filter arguments. */
    @SuppressWarnings("unchecked")
    protected static Struct mapToStruct(Map<String, Object> map) {
        var builder = Struct.newBuilder();
        for (var entry : map.entrySet()) {
            builder.putFields(entry.getKey(), toValue(entry.getValue()));
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static Value toValue(Object obj) {
        if (obj == null) {
            return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
        } else if (obj instanceof Boolean b) {
            return Value.newBuilder().setBoolValue(b).build();
        } else if (obj instanceof Number n) {
            return Value.newBuilder().setNumberValue(n.doubleValue()).build();
        } else if (obj instanceof String s) {
            return Value.newBuilder().setStringValue(s).build();
        } else if (obj instanceof Map<?, ?> m) {
            return Value.newBuilder().setStructValue(mapToStruct((Map<String, Object>) m)).build();
        } else if (obj instanceof List<?> list) {
            var listBuilder = ListValue.newBuilder();
            for (var item : list) {
                listBuilder.addValues(toValue(item));
            }
            return Value.newBuilder().setListValue(listBuilder.build()).build();
        }
        return Value.newBuilder().setStringValue(obj.toString()).build();
    }

    /** Converts a protobuf {@link Struct} back to a plain {@code Map<String, Object>}. */
    protected static Map<String, Object> structToMap(Struct struct) {
        return struct.getFieldsMap().entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> fromValue(e.getValue())));
    }

    private static Object fromValue(Value value) {
        return switch (value.getKindCase()) {
            case NULL_VALUE -> null;
            case BOOL_VALUE -> value.getBoolValue();
            case NUMBER_VALUE -> value.getNumberValue();
            case STRING_VALUE -> value.getStringValue();
            case STRUCT_VALUE -> structToMap(value.getStructValue());
            case LIST_VALUE -> value.getListValue().getValuesList().stream()
                .map(PineconeConnection::fromValue)
                .toList();
            default -> null;
        };
    }
}
