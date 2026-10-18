package space.controlnet.ae2federation.function;

/** Programs the tests share, written as a player would write them. */
final class Samples {
    static final String IRON = """
            -- Keep iron for the workshop.
            query keep_iron(min: number): bool
              local now = stock(@minecraft:iron_ingot)
              -- An unloaded network shares nothing.
              if not available(now) then
                return false
              elseif now - min > 64 * 2 then
                return true
              end
              return now >= min and online()
            end

            query workshop_filter(): filter
              return allow(@minecraft:iron_ingot, @[fluid]minecraft:water)
            end

            action signal_low_iron()
              if not keep_iron(64) then
                redstone(15)
              else
                redstone(0)
              end
              craft(@minecraft:iron_block, 2)
            end
            """;

    private Samples() {
    }
}
