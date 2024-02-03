package space.vectrix.ignite.mod;

import java.io.IOException;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.MixinService;
import org.tinylog.Logger;
import space.vectrix.ignite.agent.IgniteAgent;
import space.vectrix.ignite.launch.ember.EmberMixinContainer;
import space.vectrix.ignite.launch.ember.EmberMixinService;
import space.vectrix.ignite.launch.ember.EmberTransformer;
import space.vectrix.ignite.launch.transformer.AccessTransformerImpl;

/**
 * Represents the mod loading engine.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class ModsImpl implements Mods {
  private final ModResourceLocator resourceLocator = new ModResourceLocator();
  private final ModResourceLoader resourceLoader = new ModResourceLoader();
  private final Map<String, ModContainer> containers = new HashMap<>();
  private final List<ModResource> resources = new ArrayList<>();

  /**
   * Creates a new mod loading engine.
   *
   * @since 1.0.0
   */
  public ModsImpl() {
  }

  @Override
  public boolean loaded(final @NotNull String id) {
    return this.containers.containsKey(id);
  }

  @Override
  public @NotNull Optional<ModContainer> container(final @NotNull String id) {
    return Optional.ofNullable(this.containers.get(id));
  }

  @Override
  public @NotNull List<ModResource> resources() {
    return Collections.unmodifiableList(this.resources);
  }

  @Override
  public @NotNull Collection<ModContainer> containers() {
    return Collections.unmodifiableCollection(this.containers.values());
  }

  /**
   * Returns {@code true} if any mod resources were located, otherwise returns
   * {@code false}.
   *
   * @return whether any mod resources were located
   * @since 1.0.0
   */
  public boolean locateResources() {
    return this.resources.addAll(this.resourceLocator.locateResources());
  }

  /**
   * Returns a list of resolved mod container paths.
   *
   * @return resolved mod container paths
   * @since 1.0.0
   */
  public @NotNull List<Map.Entry<String, Path>> resolveResources() {
    final List<Map.Entry<String, Path>> targetResources = new ArrayList<>();
    for(final ModContainerImpl container : this.resourceLoader.loadResources(this)) {
      final ModResource resource = container.resource();

      if(!resource.locator().equals(ModResourceLocator.LAUNCHER_LOCATOR) && !resource.locator().equals(ModResourceLocator.GAME_LOCATOR)) {
        try {
          IgniteAgent.addJar(container.resource().path());
        } catch(final IOException exception) {
          Logger.error(exception, "Unable to add container '{}' to the classpath!", container.id());
        }
      }

      this.containers.put(container.id(), container);

      final String prettyIdentifier = String.format("%s@%s", container.id(), container.version());
      targetResources.add(new AbstractMap.SimpleEntry<>(prettyIdentifier, container.resource().path()));
    }

    return targetResources;
  }

  /**
   * Resolves the access wideners provided by the mods.
   *
   * @param transformer the transformer
   * @since 1.0.0
   */
  public void resolveWideners(final @NotNull EmberTransformer transformer) {
    final AccessTransformerImpl accessTransformer = transformer.transformer(AccessTransformerImpl.class);
    if(accessTransformer == null) return;

    for(final ModContainer container : this.containers()) {
      final ModResource resource = container.resource();

      final List<String> wideners = ((ModContainerImpl) container).config().wideners();
      if(wideners != null && !wideners.isEmpty()) {
        for(final String widener : wideners) {
          //noinspection resource
          final Path path = resource.fileSystem().getPath(widener);
          try {
            Logger.trace("Adding the access widener: {}", widener);
            accessTransformer.addWidener(path);
          } catch(final IOException exception) {
            Logger.trace(exception, "Failed to configure widener: {}", widener);
            continue;
          }

          Logger.trace("Added the access widener: {}", widener);
        }
      }
    }
  }

  /**
   * Applies the mixin transformers provided by the mods.
   *
   * @since 1.0.0
   */
  public void resolveMixins() {
    final EmberMixinService service = (EmberMixinService) MixinService.getService();
    final EmberMixinContainer handle = (EmberMixinContainer) service.getPrimaryContainer();

    for(final ModContainer container : this.containers()) {
      final ModResource resource = container.resource();

      handle.addResource(resource.path().getFileName().toString(), resource.path());

      final List<String> mixins = ((ModContainerImpl) container).config().mixins();
      if(mixins != null && !mixins.isEmpty()) {
        Mixins.addConfigurations(mixins.toArray(new String[0]));
        Logger.trace("Added the mixin configurations: {}", String.join(", ", mixins));
      }
    }
  }
}
