package japgolly.scalajs.react.experimental

import scala.scalajs.js
import japgolly.scalajs.react.{BackendScope => NormalBackendScope, _}
import japgolly.scalajs.react.macros.CompBuilderMacros
import CompScope._
import ReactComponentB.BackendKey
import StaticPropComponent.PropPair

// TODO Add: ScalaDoc, GH doc, gh-pages example.
trait StaticPropComponent {
  type StaticProps
  type DynamicProps
  type State = Unit
  type Backend
  type Node = TopNode

  final type Props        = PropPair[StaticProps, DynamicProps]
  final type BackendScope = BackendScopeMP[DynamicProps, State]

  val Component: ReactComponentC.ReqProps[Props, State, Backend, Node]

  def apply(sp: StaticProps) =
    (dp: DynamicProps) => Component(PropPair(sp, dp))
}

object StaticPropComponent {
  final case class PropPair[Static, Dynamic](static: Static, dynamic: Dynamic)

  trait Template extends StaticPropComponent {

    protected def displayName: String
    protected def configureState: ReactComponentB.P[Props] => ReactComponentB.PS[Props, State]
    protected def configureBackend: (StaticProps, BackendScope) => Backend
    protected def configureRender: NeedRender[StaticProps, DynamicProps, State, Backend, ReactComponentB.PSBR[Props, State, Backend]] => ReactComponentB.PSBR[Props, State, Backend]

    //  protected def staticPropsEquality(implicit isAnyRef: StaticProps <:< AnyRef = null): (StaticProps, StaticProps) => Boolean =
    //    if (isAnyRef eq null)
    //      _ == _
    //    else
    //      _ eq _
    protected def staticPropsEquality: (StaticProps, StaticProps) => Boolean

    protected def warnStaticPropsChange: (StaticProps, StaticProps) => Callback =
      (a, b) => Callback.warn(s"[$displayName] Static props changed\nfrom $a\n  to $b")

    protected def configure: ReactComponentB[Props, State, Backend, Node] => ReactComponentB[Props, State, Backend, Node] =
      identity

    final override val Component: ReactComponentC.ReqProps[Props, State, Backend, Node] = {
      val eq = staticPropsEquality
      def newBackendScopeMP($: NormalBackendScope[Props, State]) =
        BackendScopeMP($)(_.dynamic)

      val a = ReactComponentB[Props](displayName)
      val b = configureState(a)
      val c = b.backend($ => configureBackend($.props.runNow().static, newBackendScopeMP($)))
      val d = configureRender(new NeedRender(c.render))
      val e = d
        .domType[Node]
        .componentWillReceiveProps { i =>
          val sp1 = i.currentProps.static
          val sp2 = i.nextProps.static
          if (eq(sp1, sp2))
            Callback.empty
          else
            warnStaticPropsChange(sp1, sp2).attempt >>
              Callback {
                val raw = i.$.asInstanceOf[js.Dictionary[js.Any]]
                val bs = newBackendScopeMP(raw.asInstanceOf[NormalBackendScope[Props, State]])
                val nb = configureBackend(sp2, bs)
                raw.update(BackendKey, nb.asInstanceOf[js.Any])
              }
        }
      configure(e).build
    }
  }

  final class NeedRender[P, Q, S, B, Out] private[experimental](private val g: (DuringCallbackU[PropPair[P, Q], S, B] => ReactElement) => Out) extends AnyVal {

    def render(f: DuringCallbackU[PropPair[P, Q], S, B] => ReactElement): Out =
      g(f)

    def renderPCS(f: (DuringCallbackU[PropPair[P, Q], S, B], Q, PropsChildren, S) => ReactElement): Out =
      render($ => f($, $.props.dynamic, $.propsChildren, $.state))

    def renderPC(f: (DuringCallbackU[PropPair[P, Q], S, B], Q, PropsChildren) => ReactElement): Out =
      render($ => f($, $.props.dynamic, $.propsChildren))

    def renderPS(f: (DuringCallbackU[PropPair[P, Q], S, B], Q, S) => ReactElement): Out =
      render($ => f($, $.props.dynamic, $.state))

    def renderP(f: (DuringCallbackU[PropPair[P, Q], S, B], Q) => ReactElement): Out =
      render($ => f($, $.props.dynamic))

    def renderCS(f: (DuringCallbackU[PropPair[P, Q], S, B], PropsChildren, S) => ReactElement): Out =
      render($ => f($, $.propsChildren, $.state))

    def renderC(f: (DuringCallbackU[PropPair[P, Q], S, B], PropsChildren) => ReactElement): Out =
      render($ => f($, $.propsChildren))

    def renderS(f: (DuringCallbackU[PropPair[P, Q], S, B], S) => ReactElement): Out =
      render($ => f($, $.state))

    def render_P(f: Q => ReactElement): Out =
      render($ => f($.props.dynamic))

    def render_C(f: PropsChildren => ReactElement): Out =
      render($ => f($.propsChildren))

    def render_S(f: S => ReactElement): Out =
      render($ => f($.state))

    /**
      * Use a method named `render` in the backend, automatically populating its arguments with props, state,
      * propsChildren where needed.
      */
    def renderBackend: Out =
      macro CompBuilderMacros.renderBackendSP[P, Q, S, B]
  }
}