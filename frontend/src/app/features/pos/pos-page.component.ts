import { CurrencyPipe, DatePipe, NgFor, NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize, forkJoin, switchMap } from 'rxjs';
import { PointOfSaleProduct, PosSale } from '../../core/api/api.models';
import { PointOfSaleApiService } from '../../core/api/point-of-sale-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';

interface CartLine {
  product: PointOfSaleProduct;
  quantity: number;
}

@Component({
  selector: 'app-pos-page',
  imports: [CurrencyPipe, DatePipe, NgFor, NgIf, ReactiveFormsModule, RouterLink],
  templateUrl: './pos-page.component.html',
  styleUrl: './pos-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PosPageComponent implements OnInit {
  private readonly api = inject(PointOfSaleApiService);
  private readonly auth = inject(AuthStateService);
  private readonly formBuilder = inject(FormBuilder);

  readonly context = this.auth.context;
  readonly products = signal<PointOfSaleProduct[]>([]);
  readonly sales = signal<PosSale[]>([]);
  readonly cart = signal<CartLine[]>([]);
  readonly receipt = signal<PosSale | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly searchForm = this.formBuilder.nonNullable.group({ query: ['', Validators.maxLength(160)] });
  readonly paymentForm = this.formBuilder.nonNullable.group({
    paymentMethod: ['EFECTIVO' as 'EFECTIVO' | 'TRANSFERENCIA' | 'OTRO', Validators.required],
  });
  readonly canUsePos = computed(() => {
    const context = this.context();
    return context?.accountType === 'STAFF' && context.roles.includes('CAJERO');
  });
  readonly total = computed(() => this.cart().reduce((sum, line) => sum + line.product.salePricePen * line.quantity, 0));

  ngOnInit(): void {
    if (!this.canUsePos()) {
      this.loading.set(false);
      this.error.set('La caja exige el rol CAJERO. Administrar el tenant no concede este permiso por defecto.');
      return;
    }
    this.reload();
  }

  reload(): void {
    if (!this.canUsePos()) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const query = this.searchForm.controls.query.value.trim();
    forkJoin({ products: this.api.listProducts(query || undefined), sales: this.api.listSales() }).subscribe({
      next: ({ products, sales }) => {
        this.products.set(products.data);
        this.sales.set(sales.data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('No fue posible cargar productos y ventas. Actualiza para revalidar la sesion y el tenant.');
      },
    });
  }

  addProduct(product: PointOfSaleProduct): void {
    if (this.saving() || !this.canAdd(product)) {
      return;
    }
    this.cart.update((lines) => {
      const existing = lines.find((line) => line.product.id === product.id);
      if (!existing) {
        return [...lines, { product, quantity: 1 }];
      }
      return lines.map((line) => line.product.id === product.id ? { ...line, quantity: line.quantity + 1 } : line);
    });
  }

  setQuantity(productId: string, rawValue: string): void {
    const quantity = Number(rawValue);
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > 1_000_000) {
      this.error.set('Cada cantidad debe ser un entero positivo dentro del rango permitido.');
      return;
    }
    const line = this.cart().find((item) => item.product.id === productId);
    if (!line || (line.product.inventoryManaged && (line.product.availableQuantity ?? 0) < quantity)) {
      this.error.set('La cantidad solicitada excede el saldo disponible mostrado. El servidor volvera a validarlo al confirmar.');
      return;
    }
    this.cart.update((lines) => lines.map((item) => item.product.id === productId ? { ...item, quantity } : item));
  }

  removeProduct(productId: string): void {
    this.cart.update((lines) => lines.filter((line) => line.product.id !== productId));
  }

  confirmSale(): void {
    if (this.cart().length === 0 || this.saving() || this.paymentForm.invalid) {
      this.paymentForm.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.notice.set(null);
    const paymentMethod = this.paymentForm.controls.paymentMethod.value;
    const request = {
      operationId: crypto.randomUUID(),
      lines: this.cart().map((line) => ({ productId: line.product.id, quantity: line.quantity })),
    };
    this.api.createSale(request).pipe(
      switchMap(({ data }) => this.api.confirmSale(data.id, {
        paymentMethod,
        declaredAmountPen: Number(this.total().toFixed(2)),
      })),
      finalize(() => this.saving.set(false)),
    ).subscribe({
      next: ({ data }) => {
        this.receipt.set(data);
        this.cart.set([]);
        this.notice.set('La venta fue confirmada una sola vez y el comprobante interno ya esta disponible.');
        this.reload();
      },
      error: () => this.error.set('No fue posible confirmar la venta. El servidor conserva el saldo y rechaza confirmaciones incompletas.'),
    });
  }

  openReceipt(saleId: string): void {
    this.error.set(null);
    this.api.findSale(saleId).subscribe({
      next: ({ data }) => this.receipt.set(data),
      error: () => this.error.set('No fue posible recuperar el comprobante solicitado.'),
    });
  }

  lineSubtotal(line: CartLine): number {
    return line.product.salePricePen * line.quantity;
  }

  canAdd(product: PointOfSaleProduct): boolean {
    return !product.inventoryManaged || (product.availableQuantity ?? 0) > (this.cart().find((line) => line.product.id === product.id)?.quantity ?? 0);
  }

  availability(product: PointOfSaleProduct): string {
    return product.inventoryManaged ? `${product.availableQuantity ?? 0} disponibles` : 'Servicio sin stock';
  }

  trackProduct(_: number, product: PointOfSaleProduct): string {
    return product.id;
  }

  trackSale(_: number, sale: PosSale): string {
    return sale.id;
  }

  trackLine(_: number, line: CartLine): string {
    return line.product.id;
  }
}
