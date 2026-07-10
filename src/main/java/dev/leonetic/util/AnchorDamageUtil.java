package dev.leonetic.util;

import dev.leonetic.util.traits.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.BiFunction;

public final class AnchorDamageUtil implements Util {

    public static final double DIAMETER = 10.0;

    private AnchorDamageUtil() {
        throw new AssertionError();
    }

    public record Target(Vec3 pos, AABB box, float armor, float toughness,
                         float resistMult, int protPoints) {

        public static Target of(LivingEntity living) {
            float armor = 0f;
            float toughness = 0f;
            int protPoints = 0;
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack stack = living.getItemBySlot(slot);
                if (stack.isEmpty()) continue;
                ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
                armor     = (float) modifiers.compute(Attributes.ARMOR, armor, slot);
                toughness = (float) modifiers.compute(Attributes.ARMOR_TOUGHNESS, toughness, slot);
                protPoints += slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET ? 8 : 4;
            }
            if (protPoints > 20) protPoints = 20;

            MobEffectInstance resistance = living.getEffect(MobEffects.RESISTANCE);
            float resistMult = resistance != null
                    ? Math.max(0f, 1.0f - 0.2f * (resistance.getAmplifier() + 1)) : 1.0f;

            return new Target(living.position(), living.getBoundingBox(),
                    armor, toughness, resistMult, protPoints);
        }
    }

    public static float maxDamage(Target target, Vec3 explosionPos) {
        double dist = target.pos().distanceTo(explosionPos);
        if (dist > DIAMETER) return 0f;
        return targetDamage(target, dist, 1.0);
    }

    public static float damage(Target target, Vec3 explosionPos, BlockPos anchorPos) {
        double dist = target.pos().distanceTo(explosionPos);
        if (dist > DIAMETER) return 0f;
        return targetDamage(target, dist, exposure(explosionPos, target.box(), anchorPos));
    }

    public static float selfDamage(Vec3 explosionPos, BlockPos anchorPos) {
        double dist = mc.player.position().distanceTo(explosionPos);
        if (dist > DIAMETER) return 0f;
        double exposure = exposure(explosionPos, mc.player.getBoundingBox(), anchorPos);
        if (exposure <= 0) return 0f;
        double impact = (1.0 - dist / DIAMETER) * exposure;
        if (impact <= 0) return 0f;

        float dmg = baseDamage(impact);
        float armor     = (float) mc.player.getAttributeValue(Attributes.ARMOR);
        float toughness = (float) mc.player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        dmg = armorAbsorb(dmg, armor, toughness);

        MobEffectInstance resistance = mc.player.getEffect(MobEffects.RESISTANCE);
        if (resistance != null) dmg *= 1.0f - 0.2f * (resistance.getAmplifier() + 1);

        int epf = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = mc.player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            epf += EnchantmentUtil.getLevel(Enchantments.PROTECTION, stack);
            epf += 2 * EnchantmentUtil.getLevel(Enchantments.BLAST_PROTECTION, stack);
        }
        if (epf > 20) epf = 20;
        dmg = CombatRules.getDamageAfterMagicAbsorb(dmg, epf);
        return Math.max(dmg, 0f);
    }

    private static float targetDamage(Target target, double dist, double exposure) {
        if (exposure <= 0) return 0f;
        double impact = (1.0 - dist / DIAMETER) * exposure;
        if (impact <= 0) return 0f;

        float dmg = baseDamage(impact);
        dmg = armorAbsorb(dmg, target.armor(), target.toughness());
        dmg *= target.resistMult();
        dmg = CombatRules.getDamageAfterMagicAbsorb(dmg, target.protPoints());
        return Math.max(dmg, 0f);
    }

    private static float baseDamage(double impact) {
        float dmg = (float) ((impact * impact + impact) / 2.0 * 7.0 * DIAMETER + 1.0);
        switch (mc.level.getDifficulty()) {
            case EASY -> dmg = Math.min(dmg / 2f + 1f, dmg);
            case HARD -> dmg *= 1.5f;
            default -> { }
        }
        return dmg;
    }

    private static float armorAbsorb(float dmg, float armor, float toughness) {
        float i = 2.0f + toughness / 4.0f;
        float j = Mth.clamp(armor - dmg / i, armor * 0.2f, 20.0f);
        return dmg * (1.0f - j / 25.0f);
    }

    private static double exposure(Vec3 source, AABB box, BlockPos anchorPos) {
        double dx = box.getXsize();
        double dy = box.getYsize();
        double dz = box.getZsize();
        int steps = 2;
        int total = 0;
        int unblocked = 0;

        BiFunction<Vec3[], BlockPos, BlockHitResult> tester = (ctx, pos) -> {
            if (anchorPos != null && pos.equals(anchorPos)) return null;
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir()) return null;
            VoxelShape shape = state.getCollisionShape(mc.level, pos);
            if (shape.isEmpty()) return null;
            return shape.clip(ctx[0], ctx[1], pos);
        };

        for (int xi = 0; xi <= steps; xi++) {
            for (int yi = 0; yi <= steps; yi++) {
                for (int zi = 0; zi <= steps; zi++) {
                    Vec3 point = new Vec3(
                            box.minX + dx * xi / steps,
                            box.minY + dy * yi / steps,
                            box.minZ + dz * zi / steps);
                    Vec3[] ctx = new Vec3[]{point, source};
                    if (BlockGetter.traverseBlocks(point, source, ctx, tester, c -> null) == null) unblocked++;
                    total++;
                }
            }
        }
        return total == 0 ? 0 : (double) unblocked / total;
    }
}
