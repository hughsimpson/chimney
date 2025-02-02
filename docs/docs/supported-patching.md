# Supported Patching

Besides transforming Chimney is also able to perform patching - take a "patched" value, a "patch" value, and compute
the updated version of the patched value.

## Updating `case class`

Currently, the only supported case is updating one `case class` with another: 

!!! example

    ```scala
    //> using dep io.scalaland::chimney::{{ git.tag or local.tag }}
    import io.scalaland.chimney.dsl._
    
    case class Email(address: String) extends AnyVal
    case class Phone(number: Long) extends AnyVal
    
    case class User(id: Int, email: Email, phone: Phone)
    case class UserUpdateForm(email: String, phone: Long)
    
    val user = User(10, Email("abc@@domain.com"), Phone(1234567890L))
    val updateForm = UserUpdateForm("xyz@@domain.com", 123123123L)
    
    user.patchUsing(updateForm)
    // User(10, Email("xyz@@domain.com"), Phone(123123123L))
    ```

As we see the values from the "patch" aren't always of the same type as the values they are supposed to update.
In such case, macros use `Transformer`s logic under the hood to convert a patch into a patched value. 

!!! notice

    Currently `Patcher`s are flat - they cannot perform a nested update. 

### Ignoring fields in patches

When the patch `case class` contains a field that does not exist in patched object, Chimney will not be able to generate
`Patcher`.

!!! example

    ```scala
    //> using dep io.scalaland::chimney::{{ git.tag or local.tag }}
    import io.scalaland.chimney.dsl._
    
    case class User(id: Int, email: String, phone: Long)
    case class UserUpdateForm(email: String, phone: Long, address: String)
    
    val user = User(10, "abc@@domain.com", 1234567890L)
    
    user.patchUsing(UserUpdateForm("xyz@@domain.com", 123123123L, "some address"))
    // Chimney can't derive patcher for User with patch type UserUpdateForm
    // 
    // Field named 'address' not found in target patching type User!
    // Consult https://chimney.readthedocs.io for usage examples.
    ```

This default behavior is intentional to prevent silent oversight of typos in patcher field names.

But there is a way to ignore redundant patcher fields explicitly with `.ignoreRedundantPatcherFields` operation.

!!! example

    ```scala
    //> using dep io.scalaland::chimney::{{ git.tag or local.tag }}
    import io.scalaland.chimney.dsl._
    
    case class User(id: Int, email: String, phone: Long)
    case class UserUpdateForm(email: String, phone: Long, address: String)
    
    val user = User(10, "abc@@domain.com", 1234567890L)

    user
      .using(UserUpdateForm("xyz@@domain.com", 123123123L, "some address"))
      .ignoreRedundantPatcherFields
      .patch
    // User(10, "xyz@@domain.com", 123123123L)
    
    locally {
      // All patching derived in this scope will see these new flags (Scala 2-only syntax, see cookbook for Scala 3)
      implicit val cfg = PatcherConfiguration.default.ignoreRedundantPatcherFields
      
      user.patchUsing(UserUpdateForm("xyz@@domain.com", 123123123L, "some address"))
      // User(10, "xyz@@domain.com", 123123123L)
    }
    ```

Patching succeeded using only relevant fields that appear in the patched object and ignoring address: `String` field 
from the patch.

If the flag was enabled in the implicit config it can be disabled with `.failRedundantPatcherFields`.

!!! example

    ```scala
    //> using dep io.scalaland::chimney::{{ git.tag or local.tag }}
    import io.scalaland.chimney.dsl._

    case class User(id: Int, email: String, phone: Long)
    case class UserUpdateForm(email: String, phone: Long, address: String)
    
    val user = User(10, "abc@@domain.com", 1234567890L)
    
    // All patching derived in this scope will see these new flags (Scala 2-only syntax, see cookbook for Scala 3)
    implicit val cfg = PatcherConfiguration.default.ignoreRedundantPatcherFields
    
    user
      .using(UserUpdateForm("xyz@@domain.com", 123123123L, "some address"))
      .failRedundantPatcherFields
      .patch
    // Chimney can't derive patcher for Playground.User with patch type Playground.UserUpdateForm
    // 
    // Field named 'address' not found in target patching type Playground.User!
    // Consult https://chimney.readthedocs.io for usage examples.
    ```

### Treating `None` as no-update instead of "set to `None`"

It is possible to patch using optional values of type `Option[T]` as long as the `Transformer` is available for `T`.
If the value is present (`Some`), it’s used for patching a field in the target object; otherwise (`None`) it’s ignored
and the field value is copied from the original object.

Let’s consider the following patch:

!!! example

    ```scala
    //> using dep io.scalaland::chimney::{{ git.tag or local.tag }}
    import io.scalaland.chimney.dsl._
    
    case class User(id: Int, email: String, phone: Long)
    case class UserPatch(email: Option[String], phone: Option[Long])
    
    val user = User(10, "abc@@domain.com", 1234567890L)
    val update = UserPatch(email = Some("updated@@example.com"), phone = None)
    
    user.patchUsing(update)
    // User(10, "updated@@example.com", 1234567890L)
    ```

The field `phone` remained the same as in the original `user`, while the optional e-mail string got updated from
a patch object.

#### `Option[T]` on both sides

An interesting case appears when both the patch `case class` and the patched object define fields `f: Option[T]`
Depending on the values of `f` in the patched object and patch, we would like to apply the following semantic table:

| `patchedObject.f` | `patch.f`      | patching result |
|-------------------|----------------|-----------------|
| `None`            | `Some(value)`  | `Some(value)`   |
| `Some(value1)`    | `Some(value2)` | `Some(value2)`  |
| `None`            | `None`         | `None`          |
| `Some(value)`     | `None`         | **???**         |

When a `patch.f` contains some value, it’s immediately used for replacing a field in the target object (rows 1 and 2), 
regardless of the original object field value. When both field are `None`, the patching result is also `None` (row 3).

But if the original object contains a some value, but the patch comes with a `None`, we can do two things:

  - clear value in target object (replace it with None)
  - or ignore updating this particular field (as in the previous section)

Both choices may make perfect sense, depending on the context. By default, Chimney does the former (clears the value),
but it also gives a simple way to always ignore `None` from the patch with `.ignoreNoneInPatch` operation.

!!! example

    ```scala
    //> using dep io.scalaland::chimney::{{ git.tag or local.tag }}
    import io.scalaland.chimney.dsl._
        
    case class User(name: Option[String], age: Option[Int])
    case class UserPatch(name: Option[String], age: Option[Int])
    
    val user = User(Some("John"), Some(30))
    val userPatch = UserPatch(None, None)
    
    user.patchUsing(userPatch)
    // clears both fields: User(None, None)
    
    user
      .using(userPatch)
      .ignoreNoneInPatch
      .patch
    // ignores updating both fields: User(Some("John"), Some(30))
    
    locally {
      // All patching derived in this scope will see these new flags (Scala 2-only syntax, see cookbook for Scala 3)
      implicit val cfg = PatcherConfiguration.default.ignoreNoneInPatch
      
      user.patchUsing(userPatch)
      // ignores updating both fields: User(Some("John"), Some(30))
    }
    ```

If the flag was enabled in the implicit config it can be disabled with `.clearOnNoneInPatch`.

!!! example

    ```scala
    //> using dep io.scalaland::chimney::{{ git.tag or local.tag }}
    import io.scalaland.chimney.dsl._
        
    case class User(name: Option[String], age: Option[Int])
    case class UserPatch(name: Option[String], age: Option[Int])
    
    val user = User(Some("John"), Some(30))
    val userPatch = UserPatch(None, None)
    
    // all patching derived in this scope will see these new flags
    implicit val cfg = PatcherConfiguration.default.ignoreNoneInPatch
    
    user
      .using(userPatch)
      .clearOnNoneInPatch
      .patch
    // clears both fields: User(None, None)
    ```
 