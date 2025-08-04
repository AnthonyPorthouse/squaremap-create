package dev.porthouse.squaremap_create;

import com.mojang.logging.LogUtils;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import xyz.jpenilla.squaremap.api.*;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mod(SquaremapCreate.MODID)
public class SquaremapCreate {
    public static final String MODID = "squaremap_create";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final Key TRAINS_KEY = Key.of("trains");
    public static final Key STATIONS_KEY = Key.of("stations");

    public SquaremapCreate(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private static void registerTrainMarkers(Squaremap squaremap) {
        Create.RAILWAYS.trains.forEach((trainId, train) -> {
            List<ResourceKey<Level>> dimensions = train.getPresentDimensions();
            dimensions.forEach(level -> {
                var dimensionPos = train.getPositionInDimension(level).orElseThrow();

                squaremap.getWorldIfEnabled(WorldIdentifier.parse(level.location().toString())).ifPresent(world -> {
                    SimpleLayerProvider provider = (SimpleLayerProvider) world.layerRegistry().get(TRAINS_KEY);

                    String tooltip = train.name.getString();
                    if (train.navigation.isActive()) {
                        tooltip += "<br>Destination: " + train.navigation.destination.name;
                    }

                    if (train.getCurrentStation() != null) {
                        tooltip += "<br>Station: " + train.getCurrentStation().name;
                    }

                    Marker trainMarker = Marker.circle(Point.of(dimensionPos.getX(), dimensionPos.getZ()), 1)
                            .markerOptions(MarkerOptions.builder().hoverTooltip(tooltip).build());

                    provider.addMarker(Key.of(trainId.toString()), trainMarker);
                });
            });
        });
    }

    private static void registerStationMarkers(Squaremap squaremap) {
        Create.RAILWAYS.trackNetworks.forEach((uuid, trackGraph) -> {
            Stream<TrackNode> nodes = trackGraph.getNodes().stream().map(trackGraph::locateNode);
            Set<TrackEdge> edges = nodes.map(trackGraph::getConnectionsFrom)
                    .flatMap(node -> node.values().stream())
                    .collect(Collectors.toSet());

            edges.forEach(edge -> edge.getEdgeData().getPoints().forEach(point -> {

                if (point instanceof GlobalStation station) {

                    List.of(Level.OVERWORLD, Level.NETHER).forEach(level -> squaremap.getWorldIfEnabled(WorldIdentifier.parse(level.location().toString())).ifPresent(world -> {
                        world.layerRegistry().get(TRAINS_KEY).getMarkers().clear();
                        world.layerRegistry().get(STATIONS_KEY).getMarkers().clear();
                    }));

                    squaremap.getWorldIfEnabled(WorldIdentifier.parse(station.blockEntityDimension.location().toString())).ifPresent(world -> {
                        SimpleLayerProvider provider = (SimpleLayerProvider) world.layerRegistry().get(STATIONS_KEY);

                        provider.addMarker(
                                Key.of(station.id.toString()),
                                Marker.circle(Point.of(station.blockEntityPos.getX(), station.blockEntityPos.getZ()), 1).markerOptions(
                                        MarkerOptions.builder().hoverTooltip("Station: " + station.name).build())
                        );
                    });
                }
            }));
        });
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        //NOOP
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        Squaremap api = SquaremapProvider.get();

        List.of(Level.OVERWORLD, Level.NETHER).forEach(level -> api.getWorldIfEnabled(WorldIdentifier.parse(level.location().toString())).ifPresent(world -> {
            world.layerRegistry().register(TRAINS_KEY, SimpleLayerProvider.builder("Trains").build());
            world.layerRegistry().register(STATIONS_KEY, SimpleLayerProvider.builder("Stations").build());
        }));
    }

    @SubscribeEvent
    public void onPostServerTickEvent(ServerTickEvent.Post event) {
        Squaremap api = SquaremapProvider.get();

        clearMarkers(api);
        registerStationMarkers(api);
        registerTrainMarkers(api);
    }

    private void clearMarkers(Squaremap squaremap) {
        List.of(Level.OVERWORLD, Level.NETHER).forEach(level -> squaremap.getWorldIfEnabled(WorldIdentifier.parse(level.location().toString())).ifPresent(world -> {
            world.layerRegistry().get(TRAINS_KEY).getMarkers().clear();
            world.layerRegistry().get(STATIONS_KEY).getMarkers().clear();
        }));
    }
}
