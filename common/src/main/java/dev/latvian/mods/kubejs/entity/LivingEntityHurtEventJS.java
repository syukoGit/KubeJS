package dev.latvian.mods.kubejs.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public class LivingEntityHurtEventJS extends LivingEntityEventJS {
	private final LivingEntity entity;
	private final DamageSource source;
	private final float amount;

	public LivingEntityHurtEventJS(LivingEntity entity, DamageSource source, float amount) {
		this.entity = entity;
		this.source = source;
		this.amount = amount;
	}

	@Override
	public LivingEntity getEntity() {
		return entity;
	}

	public DamageSource getSource() {
		return source;
	}

	public float getDamage() {
		return amount;
	}
}