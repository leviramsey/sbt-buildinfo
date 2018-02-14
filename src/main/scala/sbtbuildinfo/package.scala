import sbt._

package object sbtbuildinfo {
  type BuildInfoKey = BuildInfoKey.Entry[_]
  object BuildInfoKey {
    implicit def setting[A](key: SettingKey[A]): Entry[A] = Setting(key)
    @deprecated("Explicitly wrap in BuildInfoKey.of/ofN. Or if out-of-graph execution is required use BuildInfoKey.outOfGraphUnsafe", "0.7.1")
    implicit def task[A](key: TaskKey[A]): Entry[A] = Task(key)
    implicit def taskValue[A: Manifest](task: sbt.Task[A]): Entry[A] = TaskValue(task)
    implicit def constant[A: Manifest](tuple: (String, A)): Entry[A] = Constant(tuple)
    
    def apply[A](key: SettingKey[A]): Entry[A] = Setting(key)
    @deprecated("Explicitly wrap in BuildInfoKey.of/ofN. Or if out-of-graph execution is required use BuildInfoKey.outOfGraphUnsafe", "0.7.1")
    def apply[A](key: TaskKey[A]): Entry[A] = Task(key)
    def apply[A: Manifest](tuple: (String, A)): Entry[A] = Constant(tuple)
    def map[A, B: Manifest](from: Entry[A])(fun: ((String, A)) => (String, B)): Entry[B] =
      BuildInfoKey.Mapped(from, fun)
    def action[A: Manifest](name: String)(fun: => A): Entry[A] = Action(name, () => fun)

    def of(x: Any): BuildInfoKey = macro BuildInfoKeyMacros.ofImpl
    def ofN(xs: Any*): Seq[BuildInfoKey] = macro BuildInfoKeyMacros.ofNImpl

    def outOfGraphUnsafe[A](key: TaskKey[A]): Entry[A] = Task(key)

    private[sbtbuildinfo] final case class Setting[A](scoped: SettingKey[A]) extends Entry[A] {
      def manifest = scoped.key.manifest
    }
    private[sbtbuildinfo] final case class Task[A](scoped: TaskKey[A]) extends Entry[A] {
      def manifest = scoped.key.manifest.typeArguments.head.asInstanceOf[Manifest[A]]
    }

    private[sbtbuildinfo] final case class TaskValue[A](task: sbt.Task[A])(implicit val manifest: Manifest[A])
    extends Entry[A]

    private[sbtbuildinfo] final case class Constant[A](tuple: (String, A))(implicit val manifest: Manifest[A])
    extends Entry[A]

    private[sbtbuildinfo] final case class Mapped[A, B](from: Entry[A], fun: ((String, A)) => (String, B))
                                                 (implicit val manifest: Manifest[B])
    extends Entry[B]

    private[sbtbuildinfo] final case class Action[A](name: String, fun: () => A)(implicit val manifest: Manifest[A])
    extends Entry[A]

    sealed trait Entry[A] {
      private[sbtbuildinfo] def manifest: Manifest[A]
    }
  }

  import scala.reflect.macros.blackbox

  final class BuildInfoKeyMacros(val c: blackbox.Context) {
    import c.universe._

    val BuildInfoKey = q"_root_.sbtbuildinfo.BuildInfoKey"

    def ofImpl(x: Tree): Tree = {
      x.tpe match {
        case tpe if tpe <:< typeOf[SettingKey[_]] =>
          val A = tpe.typeArgs.head
          q"$BuildInfoKey.setting[$A]($x)"

        case tpe if tpe <:< typeOf[TaskKey[_]] =>
          val A = tpe.typeArgs.head
          q"$BuildInfoKey.taskValue[$A]($x.taskValue)($x.key.manifest.typeArguments.head.asInstanceOf[Manifest[$A]])"

        case tpe if tpe <:< typeOf[(_, _)] =>
          val A = tpe.typeArgs.tail.head
          q"$BuildInfoKey.constant[$A]($x)"

        case tpe if tpe <:< typeOf[BuildInfoKey] => x
      }
    }

    def ofNImpl(xs: Tree*): Tree = q"_root_.scala.Seq(..${xs map ofImpl})"

  }
}
