package io.scalaland.chimney.dsl

/** Whether derivation should prefer total or partial transformers if both are provided for some field transformation.
  *
  * @since 0.7.0
  */
sealed abstract class ImplicitTransformerPreference

/** Tell the derivation to prefer total transformers.
  *
  * @since 0.7.0
  */
case object PreferTotalTransformer extends ImplicitTransformerPreference

/** Tell the derivation to prefer partial transformers.
  *
  * @since 0.7.0
  */
case object PreferPartialTransformer extends ImplicitTransformerPreference
